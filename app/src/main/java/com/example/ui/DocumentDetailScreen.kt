package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.DocumentEntity
import com.example.data.DocumentPageEntity
import com.example.processor.PdfManager
import com.example.processor.PdfPageSize
import com.example.processor.PdfQuality
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    viewModel: HomeViewModel,
    documentId: Long,
    onBack: () -> Unit,
    onAddPageCamera: () -> Unit,
    onAdjustCrop: (page: DocumentPageEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Ensure document and pages are actively loaded immediately upon opening
    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    val docWithPages by viewModel.activeDocumentWithPages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var activePageIndex by remember { mutableIntStateOf(0) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDocDialog by remember { mutableStateOf(false) }
    var pageToDelete by remember { mutableStateOf<DocumentPageEntity?>(null) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    // Multi-photo picker launcher for adding pages from gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onPhotosImported(uris, targetDocumentId = documentId)
        }
    }

    // Document/File picker launcher for adding PDF documents or files
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesImported(uris, targetDocumentId = documentId)
        }
    }

    val document = docWithPages?.document
    val pages = docWithPages?.pages?.sortedBy { it.pageIndex } ?: emptyList()

    // Clamp activePageIndex to valid bounds
    val safePageIndex = if (pages.isNotEmpty()) activePageIndex.coerceIn(0, pages.size - 1) else 0
    val activePage = if (pages.isNotEmpty() && safePageIndex in pages.indices) pages[safePageIndex] else null

    Scaffold(
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars),
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clickable { showRenameDialog = true }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = document?.title ?: "Document",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Rename",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (pages.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.page_n_of_m, safePageIndex + 1, pages.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Export & Share Button
                    Button(
                        onClick = { showExportSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("detail_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export / Share")
                    }

                    // More Options Dropdown
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reorder_pages_title)) },
                                leadingIcon = { Icon(Icons.Default.Reorder, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showReorderSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename_document)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showRenameDialog = true
                                }
                            )
                            activePage?.let { page ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_current_jpeg)) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        val imageFile = File(page.processedImagePath)
                                        if (imageFile.exists()) {
                                            val shareIntent = PdfManager.createShareImageIntent(
                                                context, imageFile, "${document?.title}_page_${safePageIndex + 1}"
                                            )
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Page Image"))
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_document)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showTopMenu = false
                                    showDeleteDocDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main Page Large Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading && pages.isEmpty()) {
                    CircularProgressIndicator()
                } else if (activePage != null && File(activePage.processedImagePath).exists()) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        AsyncImage(
                            model = File(activePage.processedImagePath),
                            contentDescription = "Page ${safePageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    }
                } else {
                    CircularProgressIndicator()
                }
            }

            // Page Action Quick Bar for currently displayed page
            if (activePage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reorder all pages
                        IconButton(
                            onClick = { showReorderSheet = true },
                            modifier = Modifier.testTag("action_reorder_all_pages")
                        ) {
                            Icon(Icons.Default.Reorder, contentDescription = "Rearrange pages")
                        }

                        // Rotate active page 90 degrees
                        IconButton(
                            onClick = { viewModel.rotatePage(activePage) },
                            modifier = Modifier.testTag("action_rotate_page")
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate page")
                        }

                        // Re-crop / adjust corners
                        IconButton(
                            onClick = { onAdjustCrop(activePage) },
                            modifier = Modifier.testTag("action_crop_page")
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = "Adjust crop")
                        }

                        // Move page Left
                        IconButton(
                            onClick = {
                                if (safePageIndex > 0) {
                                    viewModel.movePage(documentId, safePageIndex, safePageIndex - 1)
                                    activePageIndex = safePageIndex - 1
                                }
                            },
                            enabled = safePageIndex > 0,
                            modifier = Modifier.testTag("action_move_left")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move left")
                        }

                        // Move page Right
                        IconButton(
                            onClick = {
                                if (safePageIndex < pages.size - 1) {
                                    viewModel.movePage(documentId, safePageIndex, safePageIndex + 1)
                                    activePageIndex = safePageIndex + 1
                                }
                            },
                            enabled = safePageIndex < pages.size - 1,
                            modifier = Modifier.testTag("action_move_right")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move right")
                        }

                        // Delete Page
                        IconButton(
                            onClick = { pageToDelete = activePage },
                            modifier = Modifier.testTag("action_delete_page")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete page", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Bottom Horizontal Page Thumbnails Carousel + Add Page Button
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                        val isSelected = index == safePageIndex
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { activePageIndex = index }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp, 84.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE2E8F0))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                AsyncImage(
                                    model = File(page.processedImagePath),
                                    contentDescription = "Thumbnail ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Number Badge
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                        .size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${index + 1}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Add Page via Camera or Gallery
                    item {
                        var showAddMenu by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .size(64.dp, 84.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showAddMenu = true }
                                    .testTag("add_page_thumbnail_button")
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add Page",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Add",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Scan with Camera") },
                                    leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                                    onClick = {
                                        showAddMenu = false
                                        onAddPageCamera()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import from Gallery") },
                                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                                    onClick = {
                                        showAddMenu = false
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import PDF / Documents") },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                    onClick = {
                                        showAddMenu = false
                                        documentPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Rearrange Pages Bottom Sheet
    if (showReorderSheet && document != null && pages.isNotEmpty()) {
        ReorderPagesBottomSheet(
            pages = pages,
            onDismiss = { showReorderSheet = false },
            onSaveOrder = { newOrder ->
                viewModel.reorderDocumentPages(documentId, newOrder)
                showReorderSheet = false
                activePageIndex = 0
            }
        )
    }

    // Rename Document Dialog
    if (showRenameDialog && document != null) {
        var newTitle by remember { mutableStateOf(document.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_document)) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    label = { Text("Document Title") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_document_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameDocument(document.id, newTitle)
                        showRenameDialog = false
                    },
                    modifier = Modifier.testTag("save_rename_button")
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete Entire Document Dialog
    if (showDeleteDocDialog && document != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDocDialog = false },
            title = { Text(stringResource(R.string.delete_document)) },
            text = { Text("Are you sure you want to delete \"${document.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(document.id)
                        showDeleteDocDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDocDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete Single Page Dialog
    pageToDelete?.let { page ->
        AlertDialog(
            onDismissRequest = { pageToDelete = null },
            title = { Text(stringResource(R.string.delete_page)) },
            text = { Text("Delete Page ${safePageIndex + 1} from this document?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePage(documentId, page.id)
                        pageToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pageToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Comprehensive Export & Share Modal Bottom Sheet (PDF + JPEGs + Save to Gallery)
    if (showExportSheet && document != null) {
        ExportShareBottomSheet(
            document = document,
            pages = pages,
            activePageIndex = safePageIndex,
            onDismiss = { showExportSheet = false },
            onSharePdf = { pageSize, quality ->
                showExportSheet = false
                coroutineScope.launch {
                    val pdfFile = viewModel.exportPdf(document, pages, pageSize, quality)
                    if (pdfFile != null && pdfFile.exists()) {
                        val shareIntent = PdfManager.createSharePdfIntent(context, pdfFile, document.title)
                        context.startActivity(Intent.createChooser(shareIntent, "Share Document PDF"))
                    } else {
                        Toast.makeText(context, "Failed to compile PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onShareAllJpegs = {
                showExportSheet = false
                val files = pages.sortedBy { it.pageIndex }.map { File(it.processedImagePath) }
                val shareIntent = PdfManager.createShareMultipleImagesIntent(context, files, document.title)
                context.startActivity(Intent.createChooser(shareIntent, "Share All Pages as JPEGs"))
            },
            onShareCurrentJpeg = {
                showExportSheet = false
                activePage?.let { page ->
                    val file = File(page.processedImagePath)
                    if (file.exists()) {
                        val shareIntent = PdfManager.createShareImageIntent(context, file, "${document.title}_page_${safePageIndex + 1}")
                        context.startActivity(Intent.createChooser(shareIntent, "Share Page JPEG"))
                    }
                }
            },
            onSaveJpegsToGallery = {
                showExportSheet = false
                coroutineScope.launch {
                    val count = viewModel.saveJpegsToGallery(document, pages)
                    Toast.makeText(context, "Saved $count images to Pictures/PaperScan", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

/**
 * Interactive Reorder Pages Bottom Sheet allowing users to arrange pages in any desired order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderPagesBottomSheet(
    pages: List<DocumentPageEntity>,
    onDismiss: () -> Unit,
    onSaveOrder: (List<DocumentPageEntity>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val orderedPages = remember(pages) { mutableStateListOf(*pages.toTypedArray()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.reorder_pages_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Move pages up or down to set the order",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { onSaveOrder(orderedPages.toList()) },
                    modifier = Modifier.testTag("save_reorder_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.done))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(orderedPages, key = { _, page -> page.id }) { index, page ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(54.dp, 72.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.LightGray)
                            ) {
                                AsyncImage(
                                    model = File(page.processedImagePath),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Page Number Label
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Page ${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Filter: ${page.filterMode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Move Up
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val item = orderedPages.removeAt(index)
                                        orderedPages.add(index - 1, item)
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                            }

                            // Move Down
                            IconButton(
                                onClick = {
                                    if (index < orderedPages.size - 1) {
                                        val item = orderedPages.removeAt(index)
                                        orderedPages.add(index + 1, item)
                                    }
                                },
                                enabled = index < orderedPages.size - 1
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-featured Export & Share Bottom Sheet supporting PDF format options and JPEG sharing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportShareBottomSheet(
    document: DocumentEntity,
    pages: List<DocumentPageEntity>,
    activePageIndex: Int,
    onDismiss: () -> Unit,
    onSharePdf: (pageSize: PdfPageSize, quality: PdfQuality) -> Unit,
    onShareAllJpegs: () -> Unit,
    onShareCurrentJpeg: () -> Unit,
    onSaveJpegsToGallery: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) } // 0: PDF, 1: JPEG / Images

    var selectedPageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var selectedQuality by remember { mutableStateOf(PdfQuality.HIGH) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Text(
                text = stringResource(R.string.export_options_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${document.title} (${pages.size} ${if (pages.size == 1) "page" else "pages"})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs: PDF vs JPEG
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("PDF Document", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("JPEG Images", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (selectedTab == 0) {
                // PDF Options
                Text(
                    text = "Page Format",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedPageSize == PdfPageSize.A4,
                        onClick = { selectedPageSize = PdfPageSize.A4 }
                    )
                    Text(
                        text = "Standard A4 (Print-Grade 300 DPI)",
                        modifier = Modifier.clickable { selectedPageSize = PdfPageSize.A4 }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedPageSize == PdfPageSize.AUTO_FIT,
                        onClick = { selectedPageSize = PdfPageSize.AUTO_FIT }
                    )
                    Text(
                        text = "Auto-fit (Preserve exact scan dimensions)",
                        modifier = Modifier.clickable { selectedPageSize = PdfPageSize.AUTO_FIT }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "File Quality & Size",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                PdfQuality.values().forEach { quality ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 1.dp)
                    ) {
                        RadioButton(
                            selected = selectedQuality == quality,
                            onClick = { selectedQuality = quality }
                        )
                        Text(
                            text = quality.label,
                            modifier = Modifier.clickable { selectedQuality = quality }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onSharePdf(selectedPageSize, selectedQuality) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_export_pdf_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.share_pdf))
                }
            } else {
                // JPEG Options
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Option A: Share All Pages as JPEGs
                    OutlinedButton(
                        onClick = onShareAllJpegs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("share_all_jpegs_button")
                    ) {
                        Icon(Icons.Default.Collections, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.share_all_jpegs))
                    }

                    // Option B: Share Current Page as JPEG
                    OutlinedButton(
                        onClick = onShareCurrentJpeg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("share_current_jpeg_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.share_current_jpeg))
                    }

                    // Option C: Save to Gallery
                    Button(
                        onClick = onSaveJpegsToGallery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_gallery_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.save_to_gallery))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stringResource(R.string.created_by),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
