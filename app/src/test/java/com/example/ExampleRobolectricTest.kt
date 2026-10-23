package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DocumentEntity
import com.example.data.DocumentPageEntity
import com.example.processor.CornerPoint
import com.example.processor.Corners
import com.example.processor.DocumentProcessor
import com.example.processor.PdfManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PaperScan", appName)
    }

    @Test
    fun `test corner points serialization and parsing`() {
        val corners = Corners(
            topLeft = CornerPoint(10f, 20f),
            topRight = CornerPoint(90f, 22f),
            bottomRight = CornerPoint(88f, 180f),
            bottomLeft = CornerPoint(12f, 178f)
        )
        val str = corners.toNormalizedString()
        assertTrue(str.contains("10.0,20.0"))

        val parsed = Corners.fromNormalizedString(str, 100f, 200f)
        assertNotNull(parsed)
    }

    @Test
    fun `test sample document creation, corner detection, and enhancements`() {
        val sample = DocumentProcessor.createSampleDocumentBitmap()
        assertNotNull(sample)
        assertTrue(sample.width > 0 && sample.height > 0)

        val corners = DocumentProcessor.detectCorners(sample)
        assertNotNull(corners)
        assertTrue(corners.topLeft.x < corners.topRight.x)
        assertTrue(corners.topLeft.y < corners.bottomLeft.y)

        val warped = DocumentProcessor.warpPerspective(sample, corners)
        assertNotNull(warped)
        assertTrue(warped.width > 0 && warped.height > 0)

        // Test Magic Color enhancement
        val magicColor = DocumentProcessor.applyMagicColor(warped)
        assertNotNull(magicColor)
        assertEquals(warped.width, magicColor.width)
        assertEquals(warped.height, magicColor.height)

        // Test Manual Adjustments
        val manual = com.example.processor.ManualAdjustments(
            lightness = 0.05f,
            contrast = 1.1f,
            saturation = 1.2f,
            sharpness = 0.8f
        )
        val adjusted = DocumentProcessor.applyManualAdjustments(magicColor, manual)
        assertNotNull(adjusted)

        // Test all 4 filters
        listOf("ORIGINAL", "ENHANCED", "GRAYSCALE", "BW").forEach { filter ->
            val result = DocumentProcessor.applyEnhancements(
                source = warped,
                filterMode = filter,
                brightness = 0.05f,
                contrast = 1.1f,
                rotationDegrees = 0
            )
            assertNotNull(result)
            assertEquals(warped.width, result.width)
            assertEquals(warped.height, result.height)
        }
    }

    @Test
    fun `test jpeg sharing intents`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempFile = File(context.filesDir, "test_page_1.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val tempFile2 = File(context.filesDir, "test_page_2.jpg").apply {
            writeBytes(byteArrayOf(4, 5, 6))
        }

        // Single JPEG share intent
        val singleIntent = PdfManager.createShareImageIntent(context, tempFile, "Page 1")
        assertNotNull(singleIntent)
        assertEquals(Intent.ACTION_SEND, singleIntent.action)
        assertEquals("image/jpeg", singleIntent.type)

        // Multiple JPEGs share intent
        val multiIntent = PdfManager.createShareMultipleImagesIntent(context, listOf(tempFile, tempFile2), "Invoice")
        assertNotNull(multiIntent)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, multiIntent.action)
        assertEquals("image/jpeg", multiIntent.type)
    }

    @Test
    fun `test room database document and page crud and reordering`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val dao = db.documentDao()

        // Insert document
        val doc = DocumentEntity(
            title = "Test Receipt",
            pageCount = 2,
            coverImagePath = "/test/path/cover.jpg"
        )
        val docId = dao.insertDocument(doc)
        assertTrue(docId > 0)

        // Insert 2 pages
        val page1 = DocumentPageEntity(
            documentId = docId,
            pageIndex = 0,
            originalImagePath = "/test/orig1.jpg",
            processedImagePath = "/test/proc1.jpg",
            filterMode = "ENHANCED"
        )
        val page2 = DocumentPageEntity(
            documentId = docId,
            pageIndex = 1,
            originalImagePath = "/test/orig2.jpg",
            processedImagePath = "/test/proc2.jpg",
            filterMode = "ENHANCED"
        )
        val pageId1 = dao.insertPage(page1)
        val pageId2 = dao.insertPage(page2)
        assertTrue(pageId1 > 0 && pageId2 > 0)

        // Query with relation
        var docWithPages = dao.getDocumentWithPages(docId).first()
        assertNotNull(docWithPages)
        assertEquals(2, docWithPages!!.pages.size)

        // Reorder pages: page 2 becomes pageIndex 0, page 1 becomes pageIndex 1
        dao.updatePage(docWithPages.pages[0].copy(pageIndex = 1))
        dao.updatePage(docWithPages.pages[1].copy(pageIndex = 0))

        val reorderedPages = dao.getPagesForDocument(docId)
        assertEquals(2, reorderedPages.size)
        assertEquals(0, reorderedPages[0].pageIndex)
        assertEquals(1, reorderedPages[1].pageIndex)

        // Clean up
        dao.deleteDocumentById(docId)
        val deleted = dao.getDocumentById(docId)
        assertEquals(null, deleted)
    }
}
