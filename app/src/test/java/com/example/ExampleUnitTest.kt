package com.example

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.util.Size
import com.example.processor.CornerPoint
import com.example.processor.Corners
import com.example.processor.DocumentProcessor
import com.example.processor.ManualAdjustments
import com.example.ui.CameraResolutionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExampleUnitTest {

    @Test
    fun testCornersDefaultFull() {
        val corners = Corners.defaultFull(1000f, 1500f, insetRatio = 0.05f)
        assertEquals(50f, corners.topLeft.x, 0.01f)
        assertEquals(75f, corners.topLeft.y, 0.01f)
        assertEquals(950f, corners.topRight.x, 0.01f)
        assertEquals(75f, corners.topRight.y, 0.01f)
        assertEquals(950f, corners.bottomRight.x, 0.01f)
        assertEquals(1425f, corners.bottomRight.y, 0.01f)
        assertEquals(50f, corners.bottomLeft.x, 0.01f)
        assertEquals(1425f, corners.bottomLeft.y, 0.01f)
    }

    @Test
    fun testManualAdjustmentsDefault() {
        val adjustments = ManualAdjustments()
        assertTrue(adjustments.isDefault())

        val modified = adjustments.copy(lightness = 0.2f)
        assertFalse(modified.isDefault())
    }

    @Test
    fun testWarpPerspectiveNormal() {
        val bmp = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        val corners = Corners(
            topLeft = CornerPoint(50f, 60f),
            topRight = CornerPoint(750f, 40f),
            bottomRight = CornerPoint(780f, 960f),
            bottomLeft = CornerPoint(40f, 940f)
        )

        val warped = DocumentProcessor.warpPerspective(bmp, corners)
        assertNotNull(warped)
        assertTrue(warped.width > 0)
        assertTrue(warped.height > 0)
        bmp.recycle()
        warped.recycle()
    }

    @Test
    fun testWarpPerspectiveDegenerateCornersDoesNotCrash() {
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        // All points collinear
        val collinearCorners = Corners(
            topLeft = CornerPoint(10f, 10f),
            topRight = CornerPoint(20f, 20f),
            bottomRight = CornerPoint(30f, 30f),
            bottomLeft = CornerPoint(40f, 40f)
        )

        val warped = DocumentProcessor.warpPerspective(bmp, collinearCorners)
        assertNotNull(warped)
        assertTrue(warped.width > 0)
        assertTrue(warped.height > 0)
        bmp.recycle()
        warped.recycle()
    }

    @Test
    fun testLoupeSafeOffsetCalculations() {
        val testWidths = listOf(10f, 100f, 250f, 320f, 360f, 1080f)
        val loupeSizePx = 300f
        val touchPoints = listOf(-50f, 0f, 15f, 150f, 300f, 1200f)

        for (vw in testWidths) {
            val minX = 8f
            val maxX = kotlin.math.max(minX, vw - loupeSizePx - 8f)
            for (tx in touchPoints) {
                val rawOffsetX = tx - loupeSizePx / 2f
                val clamped = rawOffsetX.coerceIn(minOf(minX, maxX), maxOf(minX, maxX))
                assertTrue(clamped.isFinite())
                assertFalse(clamped.isNaN())
            }
        }
    }

    @Test
    fun testCameraResolutionSelectorPrioritizesHighestPractical4By3() {
        val supported = listOf(
            Size(640, 480),
            Size(1920, 1080),   // 16:9
            Size(1920, 1440),   // 4:3 (2.7MP)
            Size(3840, 2160),   // 16:9 (8.3MP)
            Size(4032, 3024),   // 4:3 (12.2MP)
            Size(3264, 2448)    // 4:3 (8.0MP)
        )

        val ranked = CameraResolutionConfig.selectBestStillCaptureSizes(supported)
        assertEquals(Size(4032, 3024), ranked[0])
        assertEquals(Size(3264, 2448), ranked[1])
        assertEquals(Size(1920, 1440), ranked[2])
    }

    @Test
    fun testUprightAspectRatioHandlesSensorRotation() {
        // Sensor outputs 4032x3024 landscape with 90 degree rotation in portrait mode -> upright 3:4 (0.75)
        val portraitRatio = CameraResolutionConfig.computeUprightAspectRatio(4032, 3024, 90)
        assertEquals(0.75f, portraitRatio, 0.001f)

        // Sensor outputs 4032x3024 with 0 degree rotation in landscape mode -> upright 4:3 (1.333)
        val landscapeRatio = CameraResolutionConfig.computeUprightAspectRatio(4032, 3024, 0)
        assertEquals(4f / 3f, landscapeRatio, 0.001f)
    }

    @Test
    fun testCalculateInSampleSizePreservesFull12MPAnd16MPCaptures() {
        // 12MP (4032x3024), 13MP (4160x3120), and 16MP (4608x3456) must NOT be downsampled
        assertEquals(1, DocumentProcessor.calculateInSampleSize(4032, 3024))
        assertEquals(1, DocumentProcessor.calculateInSampleSize(4160, 3120))
        assertEquals(1, DocumentProcessor.calculateInSampleSize(4608, 3456))
    }

    @Test
    fun testExifOrientationTransformationRotatesSensorBitmapUpright() {
        // Simulate a 200x100 landscape sensor image with ROTATE_90 EXIF tag
        val sensorBmp = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        sensorBmp.setPixel(10, 10, Color.RED)

        assertTrue(DocumentProcessor.isExifSwappingDimensions(ExifInterface.ORIENTATION_ROTATE_90))
        val upright = DocumentProcessor.applyExifOrientation(sensorBmp, ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(100, upright.width)
        assertEquals(200, upright.height)
        upright.recycle()
    }

    @Test
    fun testCropBitmapToMatchPreviewAspectRatio() {
        // When image is already 3:4 (600x800) and preview is 3:4 (0.75), dimensions remain unchanged
        val matchingBmp = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        val unchanged = DocumentProcessor.cropBitmapToMatchAspectRatio(matchingBmp, 0.75f)
        assertEquals(600, unchanged.width)
        assertEquals(800, unchanged.height)
        unchanged.recycle()

        // When a camera buffer is 9:16 (600x1066) and preview viewport is 3:4 (0.75), center-crops height to 800
        val tallBmp = Bitmap.createBitmap(600, 1066, Bitmap.Config.ARGB_8888)
        val aligned = DocumentProcessor.cropBitmapToMatchAspectRatio(tallBmp, 0.75f)
        assertEquals(600, aligned.width)
        assertEquals(800, aligned.height)
        aligned.recycle()
    }
}
