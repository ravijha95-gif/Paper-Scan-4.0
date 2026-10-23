package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.processor.Corners
import com.example.ui.AppScreen
import com.example.ui.CameraScanScreen
import com.example.ui.DocumentDetailScreen
import com.example.ui.EnhanceScreen
import com.example.ui.HomeScreen
import com.example.ui.HomeViewModel
import com.example.ui.PerspectiveCropScreen
import com.example.ui.theme.PaperScanTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaperScanTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val statusMessage by viewModel.statusMessage.collectAsState()

                LaunchedEffect(statusMessage) {
                    statusMessage?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.clearStatusMessage()
                    }
                }

                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen_navigation",
                    modifier = Modifier.fillMaxSize()
                ) { screen ->
                    when (screen) {
                        is AppScreen.Home -> {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToCamera = { isBatch ->
                                    viewModel.navigateTo(AppScreen.Camera(isBatch = isBatch))
                                },
                                onOpenDocument = { docId ->
                                    viewModel.navigateTo(AppScreen.DocumentDetail(docId))
                                }
                            )
                        }

                        is AppScreen.Camera -> {
                            CameraScanScreen(
                                viewModel = viewModel,
                                initialBatch = screen.isBatch,
                                targetDocumentId = screen.targetDocumentId,
                                onClose = { viewModel.navigateTo(AppScreen.Home) }
                            )
                        }

                        is AppScreen.Crop -> {
                            PerspectiveCropScreen(
                                originalImagePath = screen.originalImagePath,
                                targetDocumentId = screen.targetDocumentId,
                                pageId = screen.pageId,
                                isBatchRemaining = screen.isBatchRemaining,
                                onBack = {
                                    if (screen.targetDocumentId != null) {
                                        viewModel.navigateTo(AppScreen.DocumentDetail(screen.targetDocumentId))
                                    } else {
                                        viewModel.navigateTo(AppScreen.Home)
                                    }
                                },
                                onProceed = { selectedCorners ->
                                    viewModel.onCropCompleted(
                                        originalPath = screen.originalImagePath,
                                        corners = selectedCorners,
                                        targetDocumentId = screen.targetDocumentId,
                                        pageId = screen.pageId,
                                        isBatchRemaining = screen.isBatchRemaining
                                    )
                                }
                            )
                        }

                        is AppScreen.Enhance -> {
                            EnhanceScreen(
                                croppedBitmap = screen.croppedBitmap,
                                originalImagePath = screen.originalImagePath,
                                targetDocumentId = screen.targetDocumentId,
                                pageId = screen.pageId,
                                corners = screen.corners,
                                isBatchRemaining = screen.isBatchRemaining,
                                onBack = {
                                    viewModel.navigateTo(
                                        AppScreen.Crop(
                                            originalImagePath = screen.originalImagePath,
                                            targetDocumentId = screen.targetDocumentId,
                                            pageId = screen.pageId,
                                            isBatchRemaining = screen.isBatchRemaining
                                        )
                                    )
                                },
                                onDone = { filterMode, brightness, contrast, rotationDegrees, manual ->
                                    viewModel.onEnhanceCompleted(
                                        baseCroppedBitmap = screen.croppedBitmap,
                                        originalPath = screen.originalImagePath,
                                        filterMode = filterMode,
                                        brightness = brightness,
                                        contrast = contrast,
                                        rotationDegrees = rotationDegrees,
                                        corners = screen.corners,
                                        targetDocumentId = screen.targetDocumentId,
                                        pageId = screen.pageId,
                                        manual = manual
                                    )
                                }
                            )
                        }

                        is AppScreen.DocumentDetail -> {
                            DocumentDetailScreen(
                                viewModel = viewModel,
                                documentId = screen.documentId,
                                onBack = { viewModel.navigateTo(AppScreen.Home) },
                                onAddPageCamera = {
                                    viewModel.navigateTo(
                                        AppScreen.Camera(isBatch = false, targetDocumentId = screen.documentId)
                                    )
                                },
                                onAdjustCrop = { page ->
                                    viewModel.navigateTo(
                                        AppScreen.Crop(
                                            originalImagePath = page.originalImagePath,
                                            targetDocumentId = screen.documentId,
                                            pageId = page.id
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
