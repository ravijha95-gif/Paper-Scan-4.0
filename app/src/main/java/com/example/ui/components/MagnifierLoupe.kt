package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-precision floating magnifier loupe that provides a zoomed, crosshair-centered
 * view of the corner or edge currently being dragged.
 *
 * Designed to be completely crash-proof against varying screen densities, aspect ratios,
 * or extreme touch offsets.
 */
@Composable
fun MagnifierLoupe(
    bitmap: Bitmap,
    touchX: Float, // coordinate in view space (pixels)
    touchY: Float, // coordinate in view space (pixels)
    viewWidth: Float, // width of interactive preview in pixels
    viewHeight: Float, // height of interactive preview in pixels
    modifier: Modifier = Modifier
) {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return

    val density = LocalDensity.current
    val loupeSizeDp = 110.dp
    val loupeSizePx = with(density) { loupeSizeDp.toPx() }
    val zoomFactor = 2.5f

    val safeViewW = viewWidth.coerceAtLeast(10f)
    val safeViewH = viewHeight.coerceAtLeast(10f)

    // Calculate normalized position (0.0 to 1.0) on the bitmap
    val normX = (touchX / safeViewW).coerceIn(0f, 1f)
    val normY = (touchY / safeViewH).coerceIn(0f, 1f)

    val bmpCenterPixelX = normX * bitmap.width
    val bmpCenterPixelY = normY * bitmap.height

    // Calculate crop window in bitmap pixels safely
    val halfLoupe = (loupeSizePx / 2f).coerceAtLeast(10f)
    val ratioX = (safeViewW / halfLoupe).coerceAtLeast(0.1f)
    val ratioY = (safeViewH / halfLoupe).coerceAtLeast(0.1f)
    val cropRadiusX = ((bitmap.width / ratioX) / zoomFactor).coerceIn(8f, max(8f, bitmap.width / 2f))
    val cropRadiusY = ((bitmap.height / ratioY) / zoomFactor).coerceIn(8f, max(8f, bitmap.height / 2f))

    val maxW = bitmap.width
    val maxH = bitmap.height
    val srcLeft = (bmpCenterPixelX - cropRadiusX).roundToInt().coerceIn(0, max(0, maxW - 4))
    val srcTop = (bmpCenterPixelY - cropRadiusY).roundToInt().coerceIn(0, max(0, maxH - 4))
    val srcRight = (bmpCenterPixelX + cropRadiusX).roundToInt().coerceIn(srcLeft + 2, maxW)
    val srcBottom = (bmpCenterPixelY + cropRadiusY).roundToInt().coerceIn(srcTop + 2, maxH)

    // Position the loupe centered horizontally above finger touch point
    val minX = 8f
    val maxX = max(minX, safeViewW - loupeSizePx - 8f)
    val rawOffsetX = touchX - loupeSizePx / 2f
    val loupeOffsetX = rawOffsetX.coerceIn(minOf(minX, maxX), maxOf(minX, maxX))

    val minY = 8f
    val maxY = max(minY, safeViewH - loupeSizePx - 8f)
    val preferredY = touchY - loupeSizePx - 40f
    val loupeOffsetY = if (preferredY < minY) {
        // If too close to top, show below finger
        (touchY + 50f).coerceIn(minOf(minY, maxY), maxOf(minY, maxY))
    } else {
        preferredY.coerceIn(minOf(minY, maxY), maxOf(minY, maxY))
    }

    Box(
        modifier = modifier
            .offset { IntOffset(loupeOffsetX.roundToInt(), loupeOffsetY.roundToInt()) }
            .size(loupeSizeDp)
            .shadow(12.dp, CircleShape)
    ) {
        Canvas(modifier = Modifier.size(loupeSizeDp)) {
            try {
                if (bitmap.isRecycled) return@Canvas
                val canvas = drawContext.canvas.nativeCanvas

                // Circular clip path for the loupe
                val clipPath = Path().apply {
                    addCircle(size.width / 2f, size.height / 2f, size.width / 2f, Path.Direction.CW)
                }

                canvas.save()
                canvas.clipPath(clipPath)

                // Draw zoomed bitmap portion safely
                if (srcRight > srcLeft && srcBottom > srcTop) {
                    val srcRect = Rect(srcLeft, srcTop, srcRight, srcBottom)
                    val dstRect = RectF(0f, 0f, size.width, size.height)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
                }

                // Draw precision crosshairs
                val crosshairPaint = Paint().apply {
                    color = Color.rgb(0, 150, 255)
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                val cx = size.width / 2f
                val cy = size.height / 2f

                canvas.drawLine(cx - 30f, cy, cx + 30f, cy, crosshairPaint)
                canvas.drawLine(cx, cy - 30f, cx, cy + 30f, crosshairPaint)

                // Center target dot
                val dotPaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(cx, cy, 4f, dotPaint)

                canvas.restore()

                // Outer metallic / glowing blue rim border
                val rimPaint = Paint().apply {
                    color = Color.rgb(0, 119, 254)
                    strokeWidth = 6f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                canvas.drawCircle(size.width / 2f, size.height / 2f, size.width / 2f - 3f, rimPaint)

                val innerRimPaint = Paint().apply {
                    color = Color.WHITE
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                canvas.drawCircle(size.width / 2f, size.height / 2f, size.width / 2f - 6f, innerRimPaint)
            } catch (_: Throwable) {
                // Fail-safe against unexpected Skia/HWUI platform glitches
            }
        }
    }
}
