package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DocumentEntity
import com.example.data.DocumentPageEntity
import com.example.data.DocumentWithPages
import com.example.processor.Corners
import com.example.processor.DocumentProcessor
import com.example.processor.ManualAdjustments
import com.example.processor.PdfManager
import com.example.processor.PdfPageSize
import com.example.processor.PdfQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface AppScreen {
    object Home : AppScreen
    data class Camera(val isBatch: Boolean = false, val targetDocumentId: Long? = null) : AppScreen
    data class Crop(
        val originalImagePath: String,
        val targetDocumentId: Long? = null,
        val pageId: Long? = null,
        val isBatchRemaining: Boolean = false
    ) : AppScreen
    data class Enhance(
        val croppedBitmap: Bitmap,
        val originalImagePath: String,
        val targetDocumentId: Long? = null,
        val pageId: Long? = null,
        val corners: Corners,
        val isBatchRemaining: Boolean = false
    ) : AppScreen
    data class DocumentDetail(val documentId: Long) : AppScreen
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.documentDao()

    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Home)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeDocumentId = MutableStateFlow<Long?>(null)
    val activeDocumentId: StateFlow<Long?> = _activeDocumentId.asStateFlow()

    private val _activeDocumentWithPages = MutableStateFlow<DocumentWithPages?>(null)
    val activeDocumentWithPages: StateFlow<DocumentWithPages?> = _activeDocumentWithPages.asStateFlow()

    private val _batchQueue = MutableStateFlow<List<String>>(emptyList())
    val batchQueue: StateFlow<List<String>> = _batchQueue.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Filtered documents list based on search query
    val documents: StateFlow<List<DocumentEntity>> = dao.getAllDocuments()
        .combine(_searchQuery) { list, query ->
            if (query.isBlank()) list
            else list.filter { it.title.contains(query, ignoreCase = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        if (screen is AppScreen.DocumentDetail) {
            _activeDocumentId.value = screen.documentId
            loadDocument(screen.documentId)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private var documentCollectJob: kotlinx.coroutines.Job? = null

    fun loadDocument(documentId: Long) {
        _activeDocumentId.value = documentId
        documentCollectJob?.cancel()
        documentCollectJob = viewModelScope.launch {
            dao.getDocumentWithPages(documentId).collect {
                _activeDocumentWithPages.value = it
            }
        }
    }

    /**
     * Called when Camera captures a new photo.
     * Normalizes EXIF orientation and aligns aspect ratio with the visible preview viewport
     * before proceeding to batch queue or perspective crop.
     */
    fun onPhotoCaptured(
        path: String,
        isBatch: Boolean,
        targetDocumentId: Long?,
        previewAspectRatio: Float? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val normalizedPath = DocumentProcessor.normalizeCapturedImageFile(
                path = path,
                targetAspectRatio = previewAspectRatio
            )
            _isLoading.value = false
            if (isBatch) {
                _batchQueue.value = _batchQueue.value + normalizedPath
            } else {
                _currentScreen.value = AppScreen.Crop(
                    originalImagePath = normalizedPath,
                    targetDocumentId = targetDocumentId,
                    isBatchRemaining = false
                )
            }
        }
    }

    /**
     * Finish batch capturing and proceed to crop first queued page.
     */
    fun finishBatchCapturing(targetDocumentId: Long?) {
        val queue = _batchQueue.value
        if (queue.isNotEmpty()) {
            val first = queue.first()
            _batchQueue.value = queue.drop(1)
            _currentScreen.value = AppScreen.Crop(
                originalImagePath = first,
                targetDocumentId = targetDocumentId,
                isBatchRemaining = _batchQueue.value.isNotEmpty()
            )
        } else {
            _currentScreen.value = AppScreen.Home
        }
    }

    /**
     * Import multiple images from Android Photo Picker.
     */
    fun onPhotosImported(uris: List<Uri>, targetDocumentId: Long? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            val context = getApplication<Application>()
            val paths = mutableListOf<String>()

            for ((idx, uri) in uris.withIndex()) {
                val bmp = DocumentProcessor.loadBitmapFromUri(context, uri)
                if (bmp != null) {
                    val filename = "import_${System.currentTimeMillis()}_$idx.jpg"
                    val path = DocumentProcessor.saveBitmapToFile(context, bmp, filename, quality = 98)
                    paths.add(path)
                    bmp.recycle()
                }
            }

            _isLoading.value = false
            if (paths.isNotEmpty()) {
                val first = paths.first()
                _batchQueue.value = paths.drop(1)
                _currentScreen.value = AppScreen.Crop(
                    originalImagePath = first,
                    targetDocumentId = targetDocumentId,
                    isBatchRemaining = _batchQueue.value.isNotEmpty()
                )
            }
        }
    }

    /**
     * Create and import a sample invoice document for testing/instant preview.
     */
    fun importSampleDocument(targetDocumentId: Long? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val context = getApplication<Application>()
            val sampleBmp = DocumentProcessor.createSampleDocumentBitmap()
            val filename = "sample_${System.currentTimeMillis()}.jpg"
            val path = DocumentProcessor.saveBitmapToFile(context, sampleBmp, filename, quality = 98)
            sampleBmp.recycle()

            _isLoading.value = false
            _currentScreen.value = AppScreen.Crop(
                originalImagePath = path,
                targetDocumentId = targetDocumentId,
                isBatchRemaining = false
            )
        }
    }

    /**
     * Perspective crop completed, move to Enhance filter screen.
     */
    fun onCropCompleted(
        originalPath: String,
        corners: Corners,
        targetDocumentId: Long?,
        pageId: Long?,
        isBatchRemaining: Boolean
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val originalBmp = DocumentProcessor.loadBitmapFromFile(originalPath)
                if (originalBmp != null) {
                    val warped = withContext(Dispatchers.Default) {
                        DocumentProcessor.warpPerspective(originalBmp, corners)
                    }
                    originalBmp.recycle()
                    _isLoading.value = false
                    _currentScreen.value = AppScreen.Enhance(
                        croppedBitmap = warped,
                        originalImagePath = originalPath,
                        targetDocumentId = targetDocumentId,
                        pageId = pageId,
                        corners = corners,
                        isBatchRemaining = isBatchRemaining
                    )
                } else {
                    _isLoading.value = false
                    _statusMessage.value = "Failed to load image for perspective crop"
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _statusMessage.value = "Error processing crop: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    /**
     * Enhancement completed, save page to database and either proceed to next batch item or open DocumentDetail.
     */
    fun onEnhanceCompleted(
        baseCroppedBitmap: Bitmap,
        originalPath: String,
        filterMode: String,
        brightness: Float,
        contrast: Float,
        rotationDegrees: Int,
        corners: Corners,
        targetDocumentId: Long?,
        pageId: Long?,
        manual: ManualAdjustments = ManualAdjustments(lightness = brightness, contrast = contrast)
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val context = getApplication<Application>()

            val processedBmp = withContext(Dispatchers.Default) {
                DocumentProcessor.applyEnhancements(
                    source = baseCroppedBitmap,
                    filterMode = filterMode,
                    brightness = brightness,
                    contrast = contrast,
                    rotationDegrees = rotationDegrees,
                    manual = manual
                )
            }

            val processedFilename = "page_${System.currentTimeMillis()}.jpg"
            val processedPath = DocumentProcessor.saveBitmapToFile(context, processedBmp, processedFilename, quality = 98)
            processedBmp.recycle()

            val docId = if (targetDocumentId != null && targetDocumentId > 0) {
                targetDocumentId
            } else {
                // Create a new Document
                val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val newDoc = DocumentEntity(
                    title = "Scan ${timeFormat.format(Date())}",
                    pageCount = 1,
                    coverImagePath = processedPath
                )
                dao.insertDocument(newDoc)
            }

            if (pageId != null && pageId > 0) {
                // Updating existing page
                val updatedPage = DocumentPageEntity(
                    id = pageId,
                    documentId = docId,
                    pageIndex = 0, // will retain index
                    originalImagePath = originalPath,
                    processedImagePath = processedPath,
                    filterMode = filterMode,
                    brightness = brightness,
                    contrast = contrast,
                    rotationDegrees = rotationDegrees,
                    cropCorners = corners.toNormalizedString()
                )
                dao.updatePage(updatedPage)
            } else {
                // Inserting new page into document
                val existingPages = dao.getPagesForDocument(docId)
                val newPage = DocumentPageEntity(
                    documentId = docId,
                    pageIndex = existingPages.size,
                    originalImagePath = originalPath,
                    processedImagePath = processedPath,
                    filterMode = filterMode,
                    brightness = brightness,
                    contrast = contrast,
                    rotationDegrees = rotationDegrees,
                    cropCorners = corners.toNormalizedString()
                )
                dao.insertPage(newPage)
                dao.updateDocumentMeta(docId, existingPages.size + 1, processedPath)
            }

            _isLoading.value = false

            // Check if more items in batch queue
            val queue = _batchQueue.value
            if (queue.isNotEmpty()) {
                val next = queue.first()
                _batchQueue.value = queue.drop(1)
                _currentScreen.value = AppScreen.Crop(
                    originalImagePath = next,
                    targetDocumentId = docId,
                    isBatchRemaining = _batchQueue.value.isNotEmpty()
                )
            } else {
                _activeDocumentId.value = docId
                loadDocument(docId)
                _currentScreen.value = AppScreen.DocumentDetail(docId)
            }
        }
    }

    /**
     * Reorders an item in the batch capture queue.
     */
    fun reorderBatchQueue(fromIndex: Int, toIndex: Int) {
        val current = _batchQueue.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _batchQueue.value = current
        }
    }

    /**
     * Removes an item from the batch capture queue.
     */
    fun removeBatchItem(index: Int) {
        val current = _batchQueue.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _batchQueue.value = current
        }
    }

    /**
     * Reorders pages of a saved document and preserves the new order in database.
     */
    fun reorderDocumentPages(documentId: Long, newOrder: List<DocumentPageEntity>) {
        viewModelScope.launch {
            _isLoading.value = true
            newOrder.forEachIndexed { index, page ->
                dao.updatePage(page.copy(pageIndex = index))
            }
            val cover = newOrder.firstOrNull()?.processedImagePath ?: ""
            dao.updateDocumentMeta(documentId, newOrder.size, cover)
            loadDocument(documentId)
            _isLoading.value = false
        }
    }

    /**
     * Saves all document page JPEGs to the device gallery.
     */
    suspend fun saveJpegsToGallery(document: DocumentEntity, pages: List<DocumentPageEntity>): Int = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val files = pages.sortedBy { it.pageIndex }.map { File(it.processedImagePath) }
        PdfManager.saveJpegsToGallery(context, files, document.title)
    }

    /**
     * Rotate an existing page 90 degrees clockwise and update file & database.
     */
    fun rotatePage(page: DocumentPageEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            val context = getApplication<Application>()
            val currentBmp = DocumentProcessor.loadBitmapFromFile(page.processedImagePath)
            if (currentBmp != null) {
                val newRotation = (page.rotationDegrees + 90) % 360
                val rotatedBmp = DocumentProcessor.applyEnhancements(
                    source = currentBmp,
                    filterMode = "ORIGINAL",
                    rotationDegrees = 90
                )
                currentBmp.recycle()

                val newFilename = "page_rot_${System.currentTimeMillis()}.jpg"
                val newPath = DocumentProcessor.saveBitmapToFile(context, rotatedBmp, newFilename)
                rotatedBmp.recycle()

                val updated = page.copy(
                    processedImagePath = newPath,
                    rotationDegrees = newRotation
                )
                dao.updatePage(updated)
            }
            _isLoading.value = false
        }
    }

    /**
     * Move page left or right in order.
     */
    fun movePage(documentId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val pages = dao.getPagesForDocument(documentId).toMutableList()
            if (fromIndex in pages.indices && toIndex in pages.indices) {
                val moved = pages.removeAt(fromIndex)
                pages.add(toIndex, moved)
                pages.forEachIndexed { index, p ->
                    dao.updatePage(p.copy(pageIndex = index))
                }
            }
        }
    }

    /**
     * Delete an individual page from a document.
     */
    fun deletePage(documentId: Long, pageId: Long) {
        viewModelScope.launch {
            dao.deletePageById(pageId)
            val remainingPages = dao.getPagesForDocument(documentId)
            if (remainingPages.isEmpty()) {
                dao.deleteDocumentById(documentId)
                _currentScreen.value = AppScreen.Home
            } else {
                val cover = remainingPages.first().processedImagePath
                dao.updateDocumentMeta(documentId, remainingPages.size, cover)
            }
        }
    }

    fun renameDocument(documentId: Long, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            dao.renameDocument(documentId, newTitle.trim())
        }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            dao.deleteDocumentById(documentId)
            if (_activeDocumentId.value == documentId) {
                _activeDocumentId.value = null
                _currentScreen.value = AppScreen.Home
            }
        }
    }

    /**
     * Generates PDF and returns the File for sharing or local saving.
     */
    suspend fun exportPdf(
        document: DocumentEntity,
        pages: List<DocumentPageEntity>,
        pageSize: PdfPageSize,
        quality: PdfQuality
    ): File? = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val paths = pages.sortedBy { it.pageIndex }.map { it.processedImagePath }
            PdfManager.generatePdf(context, paths, document.title, pageSize, quality)
        } catch (e: Exception) {
            null
        }
    }
}
