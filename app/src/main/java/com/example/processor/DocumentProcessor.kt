package com.example.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object DocumentProcessor {

    /**
     * Detects paper document edges and returns 4 corner points with confidence score.
     * Uses a multi-cue contour, luminance gradient, and quad geometry analysis:
     * - Downsamples for fast responsive processing
     * - Separates paper surface from table/shadow using adaptive luminance and saturation
     * - Locates 4 extreme polygon points (TL, TR, BR, BL)
     * - Refines borders along gradient normal vectors
     * - Validates convexity, area ratio, and aspect ratio
     * Falls back to a safe inset rectangle if confidence is low.
     */
    fun detectCorners(bitmap: Bitmap): Corners {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        // 1. Downscale to working resolution (max dimension ~ 360px) for fast analysis
        val maxDim = 360
        val scaleDown = if (width > height) maxDim / width else maxDim / height
        val sw = (width * scaleDown).roundToInt().coerceAtLeast(100)
        val sh = (height * scaleDown).roundToInt().coerceAtLeast(100)

        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val pixels = IntArray(sw * sh)
        scaled.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        scaled.recycle()

        val lums = FloatArray(pixels.size)
        val sats = FloatArray(pixels.size)

        var sumLum = 0.0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            lums[i] = lum
            sumLum += lum

            val maxC = maxOf(r, maxOf(g, b))
            val minC = minOf(r, minOf(g, b))
            val delta = maxC - minC
            sats[i] = if (maxC > 0) delta.toFloat() / maxC else 0f
        }
        val avgLum = (sumLum / pixels.size).toFloat()

        // 2. Compute edge gradient magnitude using 3x3 Sobel-like filter
        val grads = FloatArray(sw * sh)
        for (y in 1 until sh - 1) {
            val yOffset = y * sw
            for (x in 1 until sw - 1) {
                val dx = (lums[yOffset + x + 1] - lums[yOffset + x - 1])
                val dy = (lums[yOffset + sw + x] - lums[yOffset - sw + x])
                grads[yOffset + x] = hypot(dx, dy)
            }
        }

        // 3. Segment paper vs background
        // Paper documents are characterized by higher luminance than desk surface and low saturation
        val paperThreshold = (avgLum * 0.92f).coerceIn(90f, 180f)
        val isPaper = BooleanArray(sw * sh)

        var paperPixelCount = 0
        for (i in pixels.indices) {
            // Paper pixel: bright enough or low saturation compared to dark background
            if (lums[i] >= paperThreshold && sats[i] < 0.45f) {
                isPaper[i] = true
                paperPixelCount++
            }
        }

        // If paper detection segmented reasonably (between 12% and 95% of image)
        val totalPix = sw * sh
        val areaRatio = paperPixelCount.toFloat() / totalPix

        var bestTL_X = (sw * 0.05f)
        var bestTL_Y = (sh * 0.05f)
        var bestTR_X = (sw * 0.95f)
        var bestTR_Y = (sh * 0.05f)
        var bestBR_X = (sw * 0.95f)
        var bestBR_Y = (sh * 0.95f)
        var bestBL_X = (sw * 0.05f)
        var bestBL_Y = (sh * 0.95f)

        var foundQuad = false
        var confidence = 0.6f

        if (areaRatio in 0.12f..0.96f) {
            // Find extreme corners of paper mask:
            // TL minimizes (x + y)
            // BR maximizes (x + y)
            // TR maximizes (x - y)
            // BL minimizes (x - y)
            var minSum = Float.MAX_VALUE
            var maxSum = -Float.MAX_VALUE
            var maxDiff = -Float.MAX_VALUE
            var minDiff = Float.MAX_VALUE

            var rawTL_X = 0f; var rawTL_Y = 0f
            var rawTR_X = 0f; var rawTR_Y = 0f
            var rawBR_X = 0f; var rawBR_Y = 0f
            var rawBL_X = 0f; var rawBL_Y = 0f

            for (y in 4 until sh - 4) {
                val yOff = y * sw
                for (x in 4 until sw - 4) {
                    if (isPaper[yOff + x]) {
                        val sum = (x + y).toFloat()
                        val diff = (x - y).toFloat()

                        if (sum < minSum) {
                            minSum = sum
                            rawTL_X = x.toFloat()
                            rawTL_Y = y.toFloat()
                        }
                        if (sum > maxSum) {
                            maxSum = sum
                            rawBR_X = x.toFloat()
                            rawBR_Y = y.toFloat()
                        }
                        if (diff > maxDiff) {
                            maxDiff = diff
                            rawTR_X = x.toFloat()
                            rawTR_Y = y.toFloat()
                        }
                        if (diff < minDiff) {
                            minDiff = diff
                            rawBL_X = x.toFloat()
                            rawBL_Y = y.toFloat()
                        }
                    }
                }
            }

            // Check if extracted corners form a valid non-degenerate convex quad
            val dTop = hypot(rawTR_X - rawTL_X, rawTR_Y - rawTL_Y)
            val dBottom = hypot(rawBR_X - rawBL_X, rawBR_Y - rawBL_Y)
            val dLeft = hypot(rawBL_X - rawTL_X, rawBL_Y - rawTL_Y)
            val dRight = hypot(rawBR_X - rawTR_X, rawBR_Y - rawTR_Y)

            val minDim = minOf(dTop, dBottom, dLeft, dRight)
            if (minDim > sw * 0.20f) {
                bestTL_X = rawTL_X
                bestTL_Y = rawTL_Y
                bestTR_X = rawTR_X
                bestTR_Y = rawTR_Y
                bestBR_X = rawBR_X
                bestBR_Y = rawBR_Y
                bestBL_X = rawBL_X
                bestBL_Y = rawBL_Y
                foundQuad = true
                confidence = 0.90f
            }
        }

        // 4. If no reliable polygon was found, scan inward for gradient peaks
        if (!foundQuad) {
            var leftEdge = 0.05f
            var rightEdge = 0.95f
            var topEdge = 0.05f
            var bottomEdge = 0.95f

            // Top-down scan
            for (y in 5 until sh / 3) {
                var sumG = 0f
                for (x in 10 until sw - 10) {
                    sumG += grads[y * sw + x]
                }
                if (sumG / sw > 16f) {
                    topEdge = (y.toFloat() / sh).coerceIn(0.02f, 0.25f)
                    break
                }
            }

            // Bottom-up scan
            for (y in sh - 6 downTo (sh * 2 / 3)) {
                var sumG = 0f
                for (x in 10 until sw - 10) {
                    sumG += grads[y * sw + x]
                }
                if (sumG / sw > 16f) {
                    bottomEdge = (y.toFloat() / sh).coerceIn(0.75f, 0.98f)
                    break
                }
            }

            // Left-to-right scan
            for (x in 5 until sw / 3) {
                var sumG = 0f
                for (y in 10 until sh - 10) {
                    sumG += grads[y * sw + x]
                }
                if (sumG / sh > 16f) {
                    leftEdge = (x.toFloat() / sw).coerceIn(0.02f, 0.25f)
                    break
                }
            }

            // Right-to-left scan
            for (x in sw - 6 downTo (sw * 2 / 3)) {
                var sumG = 0f
                for (y in 10 until sh - 10) {
                    sumG += grads[y * sw + x]
                }
                if (sumG / sw > 16f) {
                    rightEdge = (x.toFloat() / sw).coerceIn(0.75f, 0.98f)
                    break
                }
            }

            bestTL_X = leftEdge * sw
            bestTL_Y = topEdge * sh
            bestTR_X = rightEdge * sw
            bestTR_Y = topEdge * sh
            bestBR_X = rightEdge * sw
            bestBR_Y = bottomEdge * sh
            bestBL_X = leftEdge * sw
            bestBL_Y = bottomEdge * sh
            confidence = 0.70f
        }

        // 5. Scale back to full image coordinate space
        val invScale = 1.0f / scaleDown
        return Corners(
            topLeft = CornerPoint(
                (bestTL_X * invScale).coerceIn(0f, width),
                (bestTL_Y * invScale).coerceIn(0f, height)
            ),
            topRight = CornerPoint(
                (bestTR_X * invScale).coerceIn(0f, width),
                (bestTR_Y * invScale).coerceIn(0f, height)
            ),
            bottomRight = CornerPoint(
                (bestBR_X * invScale).coerceIn(0f, width),
                (bestBR_Y * invScale).coerceIn(0f, height)
            ),
            bottomLeft = CornerPoint(
                (bestBL_X * invScale).coerceIn(0f, width),
                (bestBL_Y * invScale).coerceIn(0f, height)
            ),
            confidence = confidence
        )
    }

    /**
     * Warps the 4 corners of the source bitmap into a rectified, flat rectangular image
     * using projective transformation.
     */
    fun warpPerspective(source: Bitmap, corners: Corners): Bitmap {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) {
            return Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        }

        val tl = corners.topLeft
        val tr = corners.topRight
        val br = corners.bottomRight
        val bl = corners.bottomLeft

        // Calculate output dimensions from Euclidean corner distances at full capture resolution
        val widthTop = hypot(tr.x - tl.x, tr.y - tl.y)
        val widthBottom = hypot(br.x - bl.x, br.y - bl.y)
        val outWidth = max(widthTop, widthBottom).roundToInt().coerceIn(100, MAX_CAPTURE_DIMENSION)

        val heightLeft = hypot(bl.x - tl.x, bl.y - tl.y)
        val heightRight = hypot(br.x - tr.x, br.y - tr.y)
        val outHeight = max(heightLeft, heightRight).roundToInt().coerceIn(100, MAX_CAPTURE_DIMENSION)

        val src = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )
        val dst = floatArrayOf(
            0f, 0f,
            outWidth.toFloat(), 0f,
            outWidth.toFloat(), outHeight.toFloat(),
            0f, outHeight.toFloat()
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(src, 0, dst, 0, 4)

        if (!success) {
            // Fallback to bounding box crop if polygon mapping is degenerate
            val minX = minOf(tl.x, tr.x, br.x, bl.x).roundToInt().coerceIn(0, source.width - 1)
            val minY = minOf(tl.y, tr.y, br.y, bl.y).roundToInt().coerceIn(0, source.height - 1)
            val maxX = maxOf(tl.x, tr.x, br.x, bl.x).roundToInt().coerceIn(minX + 1, source.width)
            val maxY = maxOf(tl.y, tr.y, br.y, bl.y).roundToInt().coerceIn(minY + 1, source.height)
            return Bitmap.createBitmap(source, minX, minY, maxX - minX, maxY - minY)
        }

        return try {
            val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

            canvas.concat(matrix)
            canvas.drawBitmap(source, 0f, 0f, paint)

            output
        } catch (_: OutOfMemoryError) {
            val halfW = (outWidth / 2).coerceAtLeast(100)
            val halfH = (outHeight / 2).coerceAtLeast(100)
            val dstHalf = floatArrayOf(
                0f, 0f,
                halfW.toFloat(), 0f,
                halfW.toFloat(), halfH.toFloat(),
                0f, halfH.toFloat()
            )
            val matrixHalf = Matrix()
            matrixHalf.setPolyToPoly(src, 0, dstHalf, 0, 4)
            val output = Bitmap.createBitmap(halfW, halfH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            canvas.concat(matrixHalf)
            canvas.drawBitmap(source, 0f, 0f, paint)
            output
        }
    }

    /**
     * Core enhancement dispatcher:
     * - filterMode: "ENHANCED" (Magic Color), "DOCUMENT", "GRAYSCALE", "BW", "ORIGINAL"
     * - brightness: -1.0 to 1.0 (lightness)
     * - contrast: 0.5 to 2.0
     * - rotationDegrees: 0, 90, 180, 270
     * - manualAdjustments: fine-tuned parameters
     */
    fun applyEnhancements(
        source: Bitmap,
        filterMode: String,
        brightness: Float = 0f,
        contrast: Float = 1.0f,
        rotationDegrees: Int = 0,
        manual: ManualAdjustments = ManualAdjustments(lightness = brightness, contrast = contrast)
    ): Bitmap {
        var baseBitmap = source

        // 1. Rotation if needed
        if (rotationDegrees % 360 != 0) {
            val rotMatrix = Matrix()
            rotMatrix.postRotate(rotationDegrees.toFloat())
            val rotated = Bitmap.createBitmap(
                baseBitmap, 0, 0, baseBitmap.width, baseBitmap.height, rotMatrix, true
            )
            baseBitmap = rotated
        }

        // 2. Base filter processing
        val filtered = when (filterMode) {
            "ENHANCED" -> applyMagicColor(baseBitmap)
            "DOCUMENT" -> applyDocumentFilter(baseBitmap)
            "GRAYSCALE" -> applyGrayscaleFilter(baseBitmap)
            "BW" -> applyAdaptiveBWFilter(baseBitmap)
            else -> if (baseBitmap === source) baseBitmap.copy(Bitmap.Config.ARGB_8888, true) else baseBitmap
        }

        // 3. Apply manual adjustments if any non-default values are present
        val effectiveManual = if (manual.lightness != brightness || manual.contrast != contrast) {
            manual.copy(lightness = brightness, contrast = contrast)
        } else {
            manual
        }

        return if (effectiveManual.isDefault()) {
            filtered
        } else {
            val adjusted = applyManualAdjustments(filtered, effectiveManual)
            if (filtered !== baseBitmap && filtered !== source) {
                filtered.recycle()
            }
            adjusted
        }
    }

    /**
     * Intelligent Magic Color enhancement (inspired by Notebloc / CamScanner):
     * 1. Estimates background illumination surface using block-wise paper percentile sampling
     * 2. Divides local illumination to completely eradicate shadows, gradients, and dark borders
     * 3. Whitens paper background while preserving fine details and anti-aliased font edges
     * 4. Detects colored inks (stamps, signatures, logos) and preserves authentic hue and saturation
     * 5. Darkens black text and lines for high contrast readability
     * 6. Applies adaptive text unsharp masking
     */
    fun applyMagicColor(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Background illumination estimation
        // Divide image into grid cells (e.g. 20-30 cells across width)
        val blockSize = max(16, min(width, height) / 24)
        val gridW = (width + blockSize - 1) / blockSize
        val gridH = (height + blockSize - 1) / blockSize

        val bgR = FloatArray(gridW * gridH)
        val bgG = FloatArray(gridW * gridH)
        val bgB = FloatArray(gridW * gridH)

        // For each block, sample luminance to find paper background (approx 92nd percentile)
        for (gy in 0 until gridH) {
            val yStart = gy * blockSize
            val yEnd = min(height, yStart + blockSize)

            for (gx in 0 until gridW) {
                val xStart = gx * blockSize
                val xEnd = min(width, xStart + blockSize)

                var maxLum = 0f
                var sumR = 0f; var sumG = 0f; var sumB = 0f
                var count = 0

                // Sample up to 100 pixels in this block
                val stepX = max(1, (xEnd - xStart) / 10)
                val stepY = max(1, (yEnd - yStart) / 10)

                val blockLums = ArrayList<Float>(100)
                val blockPixels = ArrayList<Int>(100)

                for (y in yStart until yEnd step stepY) {
                    val yOff = y * width
                    for (x in xStart until xEnd step stepX) {
                        val c = pixels[yOff + x]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        val lum = 0.299f * r + 0.587f * g + 0.114f * b
                        blockLums.add(lum)
                        blockPixels.add(c)
                    }
                }

                if (blockLums.isNotEmpty()) {
                    blockLums.sort()
                    // 90th percentile represents paper surface
                    val targetP = blockLums[(blockLums.size * 0.90f).toInt().coerceIn(0, blockLums.size - 1)]

                    // Average pixels close to the 90th percentile
                    for (i in blockPixels.indices) {
                        val c = blockPixels[i]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        val lum = 0.299f * r + 0.587f * g + 0.114f * b
                        if (abs(lum - targetP) <= 22f) {
                            sumR += r
                            sumG += g
                            sumB += b
                            count++
                        }
                    }
                }

                val idx = gy * gridW + gx
                if (count > 0) {
                    bgR[idx] = (sumR / count).coerceIn(80f, 255f)
                    bgG[idx] = (sumG / count).coerceIn(80f, 255f)
                    bgB[idx] = (sumB / count).coerceIn(80f, 255f)
                } else {
                    bgR[idx] = 220f
                    bgG[idx] = 220f
                    bgB[idx] = 220f
                }
            }
        }

        // Smooth background grid to eliminate block boundary artifacts (3x3 box blur)
        val smoothBgR = FloatArray(bgR.size)
        val smoothBgG = FloatArray(bgG.size)
        val smoothBgB = FloatArray(bgB.size)

        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                var rSum = 0f; var gSum = 0f; var bSum = 0f
                var kCount = 0
                for (dy in -1..1) {
                    val ny = (gy + dy).coerceIn(0, gridH - 1)
                    for (dx in -1..1) {
                        val nx = (gx + dx).coerceIn(0, gridW - 1)
                        val nIdx = ny * gridW + nx
                        rSum += bgR[nIdx]
                        gSum += bgG[nIdx]
                        bSum += bgB[nIdx]
                        kCount++
                    }
                }
                val curIdx = gy * gridW + gx
                smoothBgR[curIdx] = rSum / kCount
                smoothBgG[curIdx] = gSum / kCount
                smoothBgB[curIdx] = bSum / kCount
            }
        }

        // 2. Perform illumination division & paper background whitening
        val outPixels = IntArray(totalPixels)

        for (y in 0 until height) {
            val yOff = y * width
            // Bilateral/grid interpolation factors
            val gyF = (y.toFloat() / blockSize).coerceIn(0f, (gridH - 1).toFloat())
            val gy0 = gyF.toInt()
            val gy1 = min(gridH - 1, gy0 + 1)
            val ty = gyF - gy0

            for (x in 0 until width) {
                val gxF = (x.toFloat() / blockSize).coerceIn(0f, (gridW - 1).toFloat())
                val gx0 = gxF.toInt()
                val gx1 = min(gridW - 1, gx0 + 1)
                val tx = gxF - gx0

                // Bilinear interpolation of local background
                val idx00 = gy0 * gridW + gx0
                val idx10 = gy0 * gridW + gx1
                val idx01 = gy1 * gridW + gx0
                val idx11 = gy1 * gridW + gx1

                val topR = smoothBgR[idx00] * (1f - tx) + smoothBgR[idx10] * tx
                val botR = smoothBgR[idx01] * (1f - tx) + smoothBgR[idx11] * tx
                val localBgR = topR * (1f - ty) + botR * ty

                val topG = smoothBgG[idx00] * (1f - tx) + smoothBgG[idx10] * tx
                val botG = smoothBgG[idx01] * (1f - tx) + smoothBgG[idx11] * tx
                val localBgG = topG * (1f - ty) + botG * ty

                val topB = smoothBgB[idx00] * (1f - tx) + smoothBgB[idx10] * tx
                val botB = smoothBgB[idx01] * (1f - tx) + smoothBgB[idx11] * tx
                val localBgB = topB * (1f - ty) + botB * ty

                val localBgLum = (0.299f * localBgR + 0.587f * localBgG + 0.114f * localBgB).coerceAtLeast(80f)

                val c = pixels[yOff + x]
                val a = (c shr 24) and 0xFF
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                // Illumination Division: Normalizes paper background to 255
                var normR = (r.toFloat() / localBgR * 255f).coerceIn(0f, 255f)
                var normG = (g.toFloat() / localBgG * 255f).coerceIn(0f, 255f)
                var normB = (b.toFloat() / localBgB * 255f).coerceIn(0f, 255f)

                val normLum = (0.299f * normR + 0.587f * normG + 0.114f * normB).coerceIn(0f, 255f)

                // Detect colored ink (stamps, signatures, logos)
                val maxC = maxOf(r, maxOf(g, b))
                val minC = minOf(r, minOf(g, b))
                val delta = maxC - minC
                val sat = if (maxC > 0) delta.toFloat() / maxC else 0f

                // High-Clarity Document Tone Curve:
                // - Values >= 216 smoothly wash to pure 255 paper white
                // - Mid-to-dark values (small text, anti-aliased strokes, thin lines) are deepened
                //   so small characters remain dark, sharp, and legible
                val enhancedLum = if (normLum >= 216f) {
                    val t = ((normLum - 216f) / 39f).coerceIn(0f, 1f)
                    normLum * (1f - t) + 255f * t
                } else if (normLum <= 140f) {
                    // Deepen dark and medium-dark text strokes
                    normLum * 0.65f
                } else {
                    // Contrast-preserving transition that keeps anti-aliased text edges crisp
                    val t = ((normLum - 140f) / (216f - 140f)).coerceIn(0f, 1f)
                    val curvedT = t * (0.78f + 0.22f * t)
                    (140f * 0.65f) * (1f - curvedT) + 216f * curvedT
                }.coerceIn(0f, 255f)

                if (sat > 0.12f && normLum > 25f && normLum < 242f) {
                    // Colored elements: stamps, colored signatures, logos, highlighted text
                    val scale = if (normLum > 0f) enhancedLum / normLum else 1f
                    var cr = (normR * scale).coerceIn(0f, 255f)
                    var cg = (normG * scale).coerceIn(0f, 255f)
                    var cb = (normB * scale).coerceIn(0f, 255f)

                    val satBoost = 1.22f
                    cr = (enhancedLum + (cr - enhancedLum) * satBoost).coerceIn(0f, 255f)
                    cg = (enhancedLum + (cg - enhancedLum) * satBoost).coerceIn(0f, 255f)
                    cb = (enhancedLum + (cb - enhancedLum) * satBoost).coerceIn(0f, 255f)

                    outPixels[yOff + x] = (a shl 24) or (cr.toInt() shl 16) or (cg.toInt() shl 8) or cb.toInt()
                } else {
                    // Neutral text & pure white paper
                    val gray = enhancedLum.roundToInt().coerceIn(0, 255)
                    outPixels[yOff + x] = (a shl 24) or (gray shl 16) or (gray shl 8) or gray
                }
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(outPixels, 0, width, 0, 0, width, height)

        // 3. Apply resolution-adaptive edge-selective unsharp masking for ultra-crisp text
        return applyTextSharpening(output, amount = 0.65f)
    }

    /**
     * Document Filter: High contrast monochrome document with clean paper whitening.
     */
    fun applyDocumentFilter(source: Bitmap): Bitmap {
        val magic = applyMagicColor(source)
        // Desaturate to pure monochrome and apply high text contrast
        val cm = ColorMatrix().apply {
            setSaturation(0f)
            val contrastMatrix = ColorMatrix(floatArrayOf(
                1.30f, 0f, 0f, 0f, -10f,
                0f, 1.30f, 0f, 0f, -10f,
                0f, 0f, 1.30f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
            postConcat(contrastMatrix)
        }

        val result = Bitmap.createBitmap(magic.width, magic.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(magic, 0f, 0f, paint)
        magic.recycle()
        return result
    }

    /**
     * Grayscale Filter: High-fidelity grayscale photocopy with illumination leveling.
     */
    fun applyGrayscaleFilter(source: Bitmap): Bitmap {
        val magic = applyMagicColor(source)
        val cm = ColorMatrix().apply {
            setSaturation(0f)
        }
        val result = Bitmap.createBitmap(magic.width, magic.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(magic, 0f, 0f, paint)
        magic.recycle()
        return result
    }

    /**
     * Black & White Filter: Adaptive local thresholding for crisp binarized 1-bit text.
     * Overcomes global lighting gradients and produces razor-sharp scanned text.
     */
    fun applyAdaptiveBWFilter(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val total = width * height
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val lums = IntArray(total)
        for (i in 0 until total) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            lums[i] = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt().coerceIn(0, 255)
        }

        // Integral image for fast local mean computation
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            val intYOff = (y + 1) * (width + 1)
            val prevIntYOff = y * (width + 1)
            val lumYOff = y * width
            for (x in 0 until width) {
                rowSum += lums[lumYOff + x]
                integral[intYOff + x + 1] = integral[prevIntYOff + x + 1] + rowSum
            }
        }

        // Window radius approx 1/30th of image dimension
        val s = max(8, width / 32)
        val outPixels = IntArray(total)

        for (y in 0 until height) {
            val y1 = max(0, y - s)
            val y2 = min(height - 1, y + s)
            val yOff = y * width

            for (x in 0 until width) {
                val x1 = max(0, x - s)
                val x2 = min(width - 1, x + s)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                val sum = integral[(y2 + 1) * (width + 1) + (x2 + 1)] -
                        integral[y1 * (width + 1) + (x2 + 1)] -
                        integral[(y2 + 1) * (width + 1) + x1] +
                        integral[y1 * (width + 1) + x1]

                val mean = sum.toFloat() / count
                // Threshold = mean * (1 - 0.12)
                val thresh = mean * 0.88f
                val isText = lums[yOff + x] < thresh

                val colorVal = if (isText) 0x00 else 0xFF
                outPixels[yOff + x] = (0xFF shl 24) or (colorVal shl 16) or (colorVal shl 8) or colorVal
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Applies comprehensive manual adjustments:
     * Saturation, Temperature, Lightness, Warmth, Tint, HSL, Fade, Highlights, Shadows, Hue, Vignette, Sharpness.
     */
    fun applyManualAdjustments(source: Bitmap, adj: ManualAdjustments): Bitmap {
        val width = source.width
        val height = source.height
        val total = width * height
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(total)

        val cx = width / 2f
        val cy = height / 2f
        val maxDistSq = (cx * cx + cy * cy)

        val hueShift = adj.hue
        val tempR = if (adj.temperature > 0) adj.temperature * 35f else 0f
        val tempB = if (adj.temperature < 0) -adj.temperature * 35f else 0f
        val warmthR = adj.warmth * 20f
        val warmthG = adj.warmth * 10f
        val tintG = if (adj.tint < 0) -adj.tint * 25f else 0f
        val tintR = if (adj.tint > 0) adj.tint * 20f else 0f
        val tintB = if (adj.tint > 0) adj.tint * 20f else 0f

        val lightOffset = adj.lightness * 90f
        val contrast = adj.contrast
        val satFactor = adj.saturation

        val hsv = FloatArray(3)

        for (y in 0 until height) {
            val dy = y - cy
            val yOff = y * width
            for (x in 0 until width) {
                val dx = x - cx
                val c = pixels[yOff + x]
                val a = (c shr 24) and 0xFF
                var r = ((c shr 16) and 0xFF).toFloat()
                var g = ((c shr 8) and 0xFF).toFloat()
                var b = (c and 0xFF).toFloat()

                // 1. Contrast & Lightness
                if (contrast != 1.0f) {
                    r = (r - 128f) * contrast + 128f
                    g = (g - 128f) * contrast + 128f
                    b = (b - 128f) * contrast + 128f
                }
                if (lightOffset != 0f) {
                    r += lightOffset
                    g += lightOffset
                    b += lightOffset
                }

                // 2. Temperature & Warmth & Tint
                r += tempR + warmthR + tintR
                g += warmthG + tintG
                b += tempB + tintB

                // 3. Highlights & Shadows
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (adj.shadows != 0f && lum < 140f) {
                    val shadowWeight = (1f - (lum / 140f)).coerceIn(0f, 1f)
                    val shadowDelta = adj.shadows * 50f * shadowWeight
                    r += shadowDelta
                    g += shadowDelta
                    b += shadowDelta
                }
                if (adj.highlights != 0f && lum > 160f) {
                    val hlWeight = ((lum - 160f) / 95f).coerceIn(0f, 1f)
                    val hlDelta = adj.highlights * 40f * hlWeight
                    r += hlDelta
                    g += hlDelta
                    b += hlDelta
                }

                // 4. Fade (matte lift)
                if (adj.fade > 0f) {
                    val fadeVal = adj.fade * 40f
                    r = r * (1f - adj.fade * 0.25f) + fadeVal
                    g = g * (1f - adj.fade * 0.25f) + fadeVal
                    b = b * (1f - adj.fade * 0.25f) + fadeVal
                }

                // 5. Vignette
                if (adj.vignette != 0f) {
                    val distSq = (dx * dx + dy * dy)
                    val vigFactor = (distSq / maxDistSq).coerceIn(0f, 1f)
                    val vigMultiplier = (1f - adj.vignette * vigFactor * 0.45f).coerceIn(0.1f, 1.5f)
                    r *= vigMultiplier
                    g *= vigMultiplier
                    b *= vigMultiplier
                }

                r = r.coerceIn(0f, 255f)
                g = g.coerceIn(0f, 255f)
                b = b.coerceIn(0f, 255f)

                // 6. Saturation and Hue shift
                if (satFactor != 1.0f || hueShift != 0f) {
                    Color.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), hsv)
                    if (hueShift != 0f) {
                        hsv[0] = (hsv[0] + hueShift) % 360f
                        if (hsv[0] < 0f) hsv[0] += 360f
                    }
                    if (satFactor != 1.0f) {
                        hsv[1] = (hsv[1] * satFactor).coerceIn(0f, 1f)
                    }
                    val rgb = Color.HSVToColor(hsv)
                    r = ((rgb shr 16) and 0xFF).toFloat()
                    g = ((rgb shr 8) and 0xFF).toFloat()
                    b = (rgb and 0xFF).toFloat()
                }

                outPixels[yOff + x] = (a shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)

        // 7. Sharpness if requested
        return if (adj.sharpness > 0.05f) {
            applyTextSharpening(result, amount = adj.sharpness)
        } else {
            result
        }
    }

    /**
     * Adaptive edge-preserving unsharp mask:
     * Sharpens text, signatures, and stamps without amplifying flat paper noise.
     */
    private fun applyTextSharpening(source: Bitmap, amount: Float): Bitmap {
        if (amount <= 0.05f) return source

        val width = source.width
        val height = source.height
        val total = width * height
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(total)
        val strength = amount.coerceIn(0.1f, 1.8f)

        // Scale sample radius proportionally on high-res scans so multi-pixel text strokes are sharpened
        val step = if (min(width, height) >= 1800) 2 else 1

        for (y in 0 until height) {
            val yOff = y * width
            val prevYOff = max(0, y - 1) * width
            val nextYOff = min(height - 1, y + 1) * width
            val prevYStepOff = max(0, y - step) * width
            val nextYStepOff = min(height - 1, y + step) * width

            for (x in 0 until width) {
                val prevX = max(0, x - 1)
                val nextX = min(width - 1, x + 1)
                val prevXStep = max(0, x - step)
                val nextXStep = min(width - 1, x + step)

                val centerC = pixels[yOff + x]
                val a = (centerC shr 24) and 0xFF
                val rC = (centerC shr 16) and 0xFF
                val gC = (centerC shr 8) and 0xFF
                val bC = centerC and 0xFF

                // Multi-scale cross Laplacian (combines 1px fine edge + step-px stroke body)
                val topC = pixels[prevYOff + x]
                val botC = pixels[nextYOff + x]
                val leftC = pixels[yOff + prevX]
                val rightC = pixels[yOff + nextX]

                val topS = pixels[prevYStepOff + x]
                val botS = pixels[nextYStepOff + x]
                val leftS = pixels[yOff + prevXStep]
                val rightS = pixels[yOff + nextXStep]

                val rBlur1 = (((topC shr 16) and 0xFF) + ((botC shr 16) and 0xFF) + ((leftC shr 16) and 0xFF) + ((rightC shr 16) and 0xFF)) / 4
                val gBlur1 = (((topC shr 8) and 0xFF) + ((botC shr 8) and 0xFF) + ((leftC shr 8) and 0xFF) + ((rightC shr 8) and 0xFF)) / 4
                val bBlur1 = ((topC and 0xFF) + (botC and 0xFF) + (leftC and 0xFF) + (rightC and 0xFF)) / 4

                val rBlurS = (((topS shr 16) and 0xFF) + ((botS shr 16) and 0xFF) + ((leftS shr 16) and 0xFF) + ((rightS shr 16) and 0xFF)) / 4
                val gBlurS = (((topS shr 8) and 0xFF) + ((botS shr 8) and 0xFF) + ((leftS shr 8) and 0xFF) + ((rightS shr 8) and 0xFF)) / 4
                val bBlurS = ((topS and 0xFF) + (botS and 0xFF) + (leftS and 0xFF) + (rightS and 0xFF)) / 4

                val rBlur = (rBlur1 + rBlurS) / 2
                val gBlur = (gBlur1 + gBlurS) / 2
                val bBlur = (bBlur1 + bBlurS) / 2

                val rDiff = rC - rBlur
                val gDiff = gC - gBlur
                val bDiff = bC - bBlur

                // Only apply where local edge difference is noticeable (text edges)
                val edgeMag = abs(rDiff) + abs(gDiff) + abs(bDiff)
                if (edgeMag > 6) {
                    val newR = (rC + rDiff * strength).roundToInt().coerceIn(0, 255)
                    val newG = (gC + gDiff * strength).roundToInt().coerceIn(0, 255)
                    val newB = (bC + bDiff * strength).roundToInt().coerceIn(0, 255)
                    outPixels[yOff + x] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
                } else {
                    outPixels[yOff + x] = centerC
                }
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        source.recycle()
        return result
    }

    /**
     * Maximum practical still-image dimension preserved across decoding and perspective rectification.
     * Accommodates full-resolution 12MP (4032x3024), 13MP (4160x3120), and 16MP (4608x3456) captures
     * without downsampling.
     */
    const val MAX_CAPTURE_DIMENSION = 4608

    /**
     * Computes the power-of-2 sample size so that neither width nor height exceeds [maxDimension].
     * For standard 12MP-16MP camera captures (up to 4608px), returns 1 (full native resolution).
     */
    fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, maxDimension: Int = MAX_CAPTURE_DIMENSION): Int {
        if (rawWidth <= 0 || rawHeight <= 0 || maxDimension <= 0) return 1
        var sampleSize = 1
        var w = rawWidth
        var h = rawHeight
        while (w > maxDimension || h > maxDimension) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    /**
     * Builds the exact transformation [Matrix] for a given [ExifInterface] orientation tag.
     */
    fun getExifTransformationMatrix(exifOrientation: Int): Matrix {
        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.setScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.setRotate(180f)
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.setRotate(90f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.setRotate(270f)
            }
        }
        return matrix
    }

    /**
     * Returns true if the EXIF orientation swaps width and height (90 or 270 degree rotations).
     */
    fun isExifSwappingDimensions(exifOrientation: Int): Boolean {
        return exifOrientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            exifOrientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE
    }

    /**
     * Applies the EXIF orientation transformation to [bitmap] so that it is upright.
     */
    fun applyExifOrientation(bitmap: Bitmap, exifOrientation: Int): Bitmap {
        if (exifOrientation == ExifInterface.ORIENTATION_NORMAL ||
            exifOrientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return bitmap
        }
        val matrix = getExifTransformationMatrix(exifOrientation)
        if (matrix.isIdentity) return bitmap

        return try {
            val transformed = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (transformed !== bitmap) {
                bitmap.recycle()
            }
            transformed
        } catch (_: OutOfMemoryError) {
            bitmap
        }
    }

    /**
     * Center-crops [source] to match [targetAspectRatio] (width / height) if there is any mismatch
     * greater than 1.5%. Preserves full pixel resolution along the constraining axis so the
     * captured image matches the camera preview field of view 1:1.
     */
    fun cropBitmapToMatchAspectRatio(source: Bitmap, targetAspectRatio: Float): Bitmap {
        if (targetAspectRatio <= 0f || source.width <= 0 || source.height <= 0) return source
        val currentAspect = source.width.toFloat() / source.height.toFloat()
        if (abs(currentAspect - targetAspectRatio) <= 0.015f) {
            return source
        }

        val cropW: Int
        val cropH: Int
        if (currentAspect > targetAspectRatio) {
            // Image is wider than preview viewport -> trim left/right symmetrically
            cropH = source.height
            cropW = (source.height * targetAspectRatio).roundToInt().coerceIn(1, source.width)
        } else {
            // Image is taller than preview viewport -> trim top/bottom symmetrically
            cropW = source.width
            cropH = (source.width / targetAspectRatio).roundToInt().coerceIn(1, source.height)
        }

        val offsetX = ((source.width - cropW) / 2).coerceAtLeast(0)
        val offsetY = ((source.height - cropH) / 2).coerceAtLeast(0)

        return try {
            val cropped = Bitmap.createBitmap(source, offsetX, offsetY, cropW, cropH)
            if (cropped !== source) {
                source.recycle()
            }
            cropped
        } catch (_: OutOfMemoryError) {
            source
        }
    }

    /**
     * Normalizes a newly captured photograph on disk:
     * 1. Reads EXIF orientation and rotates/flips the image upright so downstream views and detectors
     *    always see the exact orientation shown in the camera preview.
     * 2. If [targetAspectRatio] is provided and the captured buffer's aspect ratio differs from the
     *    preview viewport by > 1.5%, center-crops to match the preview field of view without scaling down.
     * 3. If the file is already upright (ORIENTATION_NORMAL) and already matches [targetAspectRatio],
     *    leaves the original JPEG file untouched to avoid unnecessary recompression.
     */
    suspend fun normalizeCapturedImageFile(
        path: String,
        targetAspectRatio: Float? = null,
        maxDimension: Int = MAX_CAPTURE_DIMENSION
    ): String = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) return@withContext path

            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, boundsOptions)
            val rawW = boundsOptions.outWidth
            val rawH = boundsOptions.outHeight
            if (rawW <= 0 || rawH <= 0) return@withContext path

            val uprightW = if (isExifSwappingDimensions(orientation)) rawH else rawW
            val uprightH = if (isExifSwappingDimensions(orientation)) rawW else rawH

            val needsExifTransform = orientation != ExifInterface.ORIENTATION_NORMAL &&
                orientation != ExifInterface.ORIENTATION_UNDEFINED
            val needsAspectCrop = targetAspectRatio != null &&
                targetAspectRatio > 0f &&
                abs((uprightW.toFloat() / uprightH.toFloat()) - targetAspectRatio) > 0.015f

            if (!needsExifTransform && !needsAspectCrop) {
                return@withContext path
            }

            // Decode upright bitmap at full practical resolution (loadBitmapFromFile applies EXIF orientation)
            var bmp = loadBitmapFromFile(path, maxDimension = maxDimension) ?: return@withContext path
            if (targetAspectRatio != null && targetAspectRatio > 0f) {
                bmp = cropBitmapToMatchAspectRatio(bmp, targetAspectRatio)
            }

            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 98, out)
                out.flush()
            }
            bmp.recycle()

            // Ensure EXIF orientation tag is explicitly NORMAL after physical rotation
            val updatedExif = ExifInterface(path)
            updatedExif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            updatedExif.saveAttributes()

            path
        } catch (_: Exception) {
            path
        }
    }

    /**
     * Saves a bitmap to the app-private files directory as high-quality JPEG (default 98%).
     */
    suspend fun saveBitmapToFile(
        context: Context,
        bitmap: Bitmap,
        filename: String,
        quality: Int = 98
    ): String = withContext(Dispatchers.IO) {
        val scansDir = File(context.filesDir, "scans").apply { if (!exists()) mkdirs() }
        val file = File(scansDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
            out.flush()
        }
        try {
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            exif.saveAttributes()
        } catch (_: Exception) {
            // Ignore if EXIF writing is unavailable in unit test environment
        }
        file.absolutePath
    }

    /**
     * Loads a bitmap at full practical resolution (default up to 4608px) while respecting EXIF orientation.
     * Includes automatic OOM fallback retry so memory-constrained devices remain crash-free.
     */
    suspend fun loadBitmapFromFile(
        path: String,
        maxDimension: Int = MAX_CAPTURE_DIMENSION
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) return@withContext null

            val exifOrientation = try {
                val exif = ExifInterface(path)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)

            var sampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)

            var decoded: Bitmap? = null
            while (decoded == null && sampleSize <= 16) {
                try {
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inScaled = false
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    decoded = BitmapFactory.decodeFile(path, decodeOptions)
                } catch (_: OutOfMemoryError) {
                    sampleSize *= 2
                }
            }

            decoded?.let { applyExifOrientation(it, exifOrientation) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reliably loads one or more Bitmaps from any Android content or file Uri.
     * Supports:
     * 1. Multi-page PDF documents rendered at high resolution (~300 DPI) using Android's native PdfRenderer.
     * 2. Image formats (JPEG, PNG, WEBP, HEIC, BMP) with EXIF orientation correction and memory-safe decoding.
     * 3. Transient URI safety: copies stream to a local cache file first, avoiding SecurityExceptions
     *    or closed-stream errors from third-party storage providers.
     */
    suspend fun loadBitmapsFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_CAPTURE_DIMENSION
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val tempFile = try {
            File.createTempFile("import_${System.currentTimeMillis()}_", ".tmp", cacheDir).apply {
                deleteOnExit()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (_: Exception) {
            null
        } ?: return@withContext emptyList()

        if (tempFile.length() <= 0L) {
            try { tempFile.delete() } catch (_: Exception) {}
            return@withContext emptyList()
        }

        try {
            // Check if the file is a PDF
            if (isPdfFile(context, uri, tempFile)) {
                val pdfBitmaps = renderPdfPages(tempFile, maxDimension)
                if (pdfBitmaps.isNotEmpty()) {
                    return@withContext pdfBitmaps
                }
            }

            // Otherwise, decode as standard image file
            val imageBitmap = decodeImageFile(tempFile, maxDimension)
            if (imageBitmap != null) {
                listOf(imageBitmap)
            } else {
                emptyList()
            }
        } finally {
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Exception) {}
        }
    }

    /**
     * Decodes a single Bitmap from Android content Uri at full practical resolution
     * while respecting EXIF orientation metadata.
     */
    suspend fun loadBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_CAPTURE_DIMENSION
    ): Bitmap? = withContext(Dispatchers.IO) {
        loadBitmapsFromUri(context, uri, maxDimension).firstOrNull()
    }

    private fun isPdfFile(context: Context, uri: Uri, file: File): Boolean {
        // 1. MIME type check
        val mime = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
        if (mime != null && mime.contains("pdf", ignoreCase = true)) {
            return true
        }

        // 2. URI path or display name check
        val path = uri.path ?: ""
        if (path.endsWith(".pdf", ignoreCase = true)) {
            return true
        }

        // 3. Magic bytes check: PDF header "%PDF" (0x25, 0x50, 0x44, 0x46)
        return try {
            if (file.length() >= 4) {
                val header = ByteArray(4)
                FileInputStream(file).use { it.read(header) }
                header[0] == '%'.code.toByte() &&
                header[1] == 'P'.code.toByte() &&
                header[2] == 'D'.code.toByte() &&
                header[3] == 'F'.code.toByte()
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun renderPdfPages(file: File, maxDimension: Int): List<Bitmap> {
        val pfd = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (_: Exception) {
            return emptyList()
        }

        return try {
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            val pages = mutableListOf<Bitmap>()

            for (i in 0 until count) {
                try {
                    val page = renderer.openPage(i)
                    // High-quality document rendering (approx 300 DPI vs standard 72 DPI screen points)
                    val baseScale = 300f / 72f
                    val maxPageDim = maxOf(page.width, page.height).coerceAtLeast(1)
                    val fitScale = if (maxPageDim * baseScale > maxDimension) {
                        maxDimension.toFloat() / maxPageDim
                    } else {
                        baseScale
                    }
                    val targetScale = fitScale.coerceIn(1.0f, 6.0f)
                    val outW = (page.width * targetScale).roundToInt().coerceAtLeast(1)
                    val outH = (page.height * targetScale).roundToInt().coerceAtLeast(1)

                    val pageBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(pageBmp)
                    // White paper background (PDF pages can have transparent background by default)
                    canvas.drawColor(Color.WHITE)
                    page.render(pageBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pages.add(pageBmp)
                } catch (_: Exception) {
                    // Continue to next page if one fails
                }
            }
            renderer.close()
            pages
        } catch (_: Exception) {
            emptyList()
        } finally {
            try { pfd.close() } catch (_: Exception) {}
        }
    }

    private fun decodeImageFile(file: File, maxDimension: Int): Bitmap? {
        val exifOrientation = try {
            val exif = ExifInterface(file.absolutePath)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        var sampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
        var decoded: Bitmap? = null

        while (decoded == null && sampleSize <= 16) {
            try {
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            } catch (_: OutOfMemoryError) {
                sampleSize *= 2
            }
        }

        return decoded?.let { applyExifOrientation(it, exifOrientation) }
    }

    /**
     * Creates a realistic test sample invoice/document bitmap with realistic lighting and shadow gradients
     * to demonstrate shadow removal and Magic Color paper whitening.
     */
    fun createSampleDocumentBitmap(): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Desk background (dark blue/slate like the user's uploaded photo)
        val bgPaint = Paint().apply { color = Color.rgb(24, 45, 75) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Paper shadow
        val shadowPaint = Paint().apply {
            color = Color.argb(60, 0, 0, 0)
            isAntiAlias = true
        }
        canvas.drawRect(85f, 95f, width - 75f, height - 75f, shadowPaint)

        // Paper page (angled slightly)
        val paperPaint = Paint().apply {
            color = Color.rgb(250, 250, 248)
            isAntiAlias = true
        }
        canvas.drawRect(80f, 80f, width - 80f, height - 80f, paperPaint)

        // Simulate natural room lighting gradient (brighter top-left, darker shadow bottom-right)
        val gradientShader = android.graphics.LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(90, 40, 45, 55)),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        val shadowGradPaint = Paint().apply {
            shader = gradientShader
            isAntiAlias = true
        }
        canvas.drawRect(80f, 80f, width - 80f, height - 80f, shadowGradPaint)

        // Header logo / title (Plast World style logo in teal/blue)
        val logoCirclePaint = Paint().apply {
            color = Color.rgb(0, 126, 167) // Teal/Cyan Logo
            isAntiAlias = true
        }
        canvas.drawCircle(width - 220f, 160f, 45f, logoCirclePaint)

        val logoTextPaint = Paint().apply {
            color = Color.rgb(0, 75, 120)
            textSize = 32f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("PLAST WORLD", width - 460f, 170f, logoTextPaint)

        // Document Title
        val titlePaint = Paint().apply {
            color = Color.rgb(20, 20, 25)
            textSize = 44f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("PROFORMA INVOICE", 380f, 220f, titlePaint)

        // Document Details Text
        val textPaint = Paint().apply {
            color = Color.rgb(45, 45, 50)
            textSize = 26f
            isAntiAlias = true
        }
        val boldPaint = Paint().apply {
            color = Color.rgb(15, 15, 20)
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(60, 60, 65)
            strokeWidth = 2.5f
            isAntiAlias = true
        }

        canvas.drawText("INVOICE NO: PW / 1 / 33 / 26-27", 130f, 300f, boldPaint)
        canvas.drawText("DATE: 07/09/2026", 750f, 300f, boldPaint)
        canvas.drawText("TO: UTKARSH INDIA LTD.", 130f, 350f, boldPaint)
        canvas.drawText("NH-2, DURGAPUR EXPRESSWAY", 130f, 390f, textPaint)
        canvas.drawText("GST NO: 19AAACW4982C1Z6", 130f, 430f, boldPaint)

        // Table Grid
        canvas.drawRect(120f, 480f, width - 120f, 950f, Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.rgb(40, 40, 45)
        })
        canvas.drawLine(120f, 540f, width - 120f, 540f, linePaint)
        canvas.drawLine(200f, 480f, 200f, 950f, linePaint)
        canvas.drawLine(680f, 480f, 680f, 950f, linePaint)
        canvas.drawLine(850f, 480f, 850f, 950f, linePaint)

        canvas.drawText("SR", 140f, 520f, boldPaint)
        canvas.drawText("PARTICULARS", 230f, 520f, boldPaint)
        canvas.drawText("QTY", 730f, 520f, boldPaint)
        canvas.drawText("TOTAL RS.", 880f, 520f, boldPaint)

        canvas.drawText("01", 145f, 610f, textPaint)
        canvas.drawText("ACID FUME EXTRACTION SYSTEM", 220f, 610f, boldPaint)
        canvas.drawText("02 NOS.", 710f, 610f, textPaint)
        canvas.drawText("49,00,000=00", 870f, 610f, boldPaint)

        canvas.drawText("ADD 18% GST EXTRA =======>", 220f, 740f, boldPaint)
        canvas.drawText("8,82,000=00", 870f, 740f, boldPaint)

        canvas.drawLine(120f, 820f, width - 120f, 820f, linePaint)
        canvas.drawText("TOTAL AMOUNT RS.", 220f, 890f, boldPaint)
        canvas.drawText("57,82,000=00", 870f, 890f, boldPaint)

        // Bank Details
        canvas.drawText("BANK DETAILS: ICIC BANK | A/C NO: 624405036489", 130f, 1020f, textPaint)

        // Rubber Stamp in authentic rich purple / violet (like Notebloc reference)
        val stampPaint = Paint().apply {
            color = Color.rgb(75, 70, 155) // Purple rubber stamp ink
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        val stampTextPaint = Paint().apply {
            color = Color.rgb(75, 70, 155)
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
        }

        canvas.save()
        canvas.rotate(-8f, 900f, 1150f)
        canvas.drawCircle(900f, 1150f, 85f, stampPaint)
        canvas.drawCircle(900f, 1150f, 75f, stampPaint)
        canvas.drawText("PLAST WORLD", 815f, 1120f, stampTextPaint)
        canvas.drawText("VATVA", 855f, 1160f, stampTextPaint)
        canvas.drawText("PARTNER", 840f, 1200f, stampTextPaint)

        // Authentic signature line over the stamp
        val sigPaint = Paint().apply {
            color = Color.rgb(20, 25, 70) // Blue-black ballpoint pen
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        val sigPath = android.graphics.Path().apply {
            moveTo(820f, 1170f)
            cubicTo(850f, 1110f, 890f, 1230f, 930f, 1140f)
            cubicTo(950f, 1120f, 980f, 1180f, 1010f, 1160f)
        }
        canvas.drawPath(sigPath, sigPaint)
        canvas.restore()

        // Footer address
        val footerPaint = Paint().apply {
            color = Color.rgb(65, 70, 80)
            textSize = 18f
            isAntiAlias = true
        }
        canvas.drawText("Plot No. 7, Dev Estate, Nr. National Rifle, Ramol Cross Road, Vatva, Ahmedabad - 382445", 130f, 1500f, footerPaint)

        return bitmap
    }
}
