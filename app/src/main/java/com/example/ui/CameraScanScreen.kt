package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.util.Rational
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionFilter
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.ScanGuideBlue
import com.example.ui.theme.ScanLaserLine
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared CameraX configuration helpers ensuring:
 * 1. Consistent 4:3 sensor aspect ratio across Preview, ImageCapture, and ImageAnalysis.
 * 2. Selection of the highest practical still-image resolution supported by the rear camera.
 * 3. Accurate upright aspect ratio computation across portrait and landscape rotations.
 */
object CameraResolutionConfig {

    private const val PREFERRED_SENSOR_RATIO = 4f / 3f
    private const val ASPECT_TOLERANCE = 0.06f
    private const val MAX_PRACTICAL_PIXELS = 20_000_000L // ~20MP ceiling for fast mobile memory handling

    /**
     * Ranks supported camera output sizes for document scanning:
     * - Prioritizes sizes matching the main sensor's native 4:3 aspect ratio.
     * - Selects the highest practical still resolution (up to 20MP) so fine print and stamps are razor-sharp.
     * - Never forces an unsupported size; always filters/orders the device's own [supportedSizes].
     */
    fun selectBestStillCaptureSizes(
        supportedSizes: List<Size>,
        preferredAspectRatio: Float = PREFERRED_SENSOR_RATIO
    ): List<Size> {
        if (supportedSizes.isEmpty()) return emptyList()

        val matchingAspectPractical = mutableListOf<Size>()
        val matchingAspectUltraLarge = mutableListOf<Size>()
        val otherSizes = mutableListOf<Size>()

        for (size in supportedSizes) {
            val w = max(size.width, size.height)
            val h = min(size.width, size.height)
            if (h <= 0) continue

            val ratio = w.toFloat() / h.toFloat()
            val pixels = w.toLong() * h.toLong()
            val isPreferredRatio = abs(ratio - preferredAspectRatio) <= ASPECT_TOLERANCE

            if (isPreferredRatio) {
                if (pixels <= MAX_PRACTICAL_PIXELS) {
                    matchingAspectPractical.add(size)
                } else {
                    matchingAspectUltraLarge.add(size)
                }
            } else {
                otherSizes.add(size)
            }
        }

        val areaDescending = Comparator<Size> { a, b ->
            val areaA = a.width.toLong() * a.height.toLong()
            val areaB = b.width.toLong() * b.height.toLong()
            areaB.compareTo(areaA)
        }

        matchingAspectPractical.sortWith(areaDescending)
        matchingAspectUltraLarge.sortWith(areaDescending)
        otherSizes.sortWith(areaDescending)

        val result = ArrayList<Size>(supportedSizes.size)
        result.addAll(matchingAspectPractical)
        result.addAll(matchingAspectUltraLarge)
        result.addAll(otherSizes)

        return if (result.isNotEmpty()) result else supportedSizes
    }

    fun buildPreviewResolutionSelector(): ResolutionSelector {
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1920, 1440),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
    }

    fun buildImageCaptureResolutionSelector(): ResolutionSelector {
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .setResolutionFilter(
                ResolutionFilter { supportedSizes, _ ->
                    selectBestStillCaptureSizes(supportedSizes)
                }
            )
            .build()
    }

    fun buildImageAnalysisResolutionSelector(): ResolutionSelector {
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(960, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
    }

    /**
     * Computes the upright display aspect ratio (width / height) after applying [rotationDegrees].
     */
    fun computeUprightAspectRatio(width: Int, height: Int, rotationDegrees: Int): Float {
        if (width <= 0 || height <= 0) return 3f / 4f
        val swapped = (rotationDegrees % 180) != 0
        val uprightW = if (swapped) height else width
        val uprightH = if (swapped) width else height
        return uprightW.toFloat() / uprightH.toFloat()
    }
}

@Composable
fun CameraScanScreen(
    viewModel: HomeViewModel,
    initialBatch: Boolean = false,
    targetDocumentId: Long? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val batchQueue by viewModel.batchQueue.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler {
        onClose()
    }

    var isBatchMode by remember { mutableStateOf(initialBatch) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var previewUseCase by remember { mutableStateOf<Preview?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // Track the exact camera stream aspect ratio (3:4 in portrait, 4:3 in landscape by default)
    var cameraAspectRatio by remember(isLandscape) {
        mutableFloatStateOf(if (isLandscape) 4f / 3f else 3f / 4f)
    }
    var resolutionLabel by remember { mutableStateOf<String?>(null) }
    var currentZoomRatio by remember { mutableFloatStateOf(1.0f) }

    // Keep targetRotation synchronized with the device's display orientation
    DisposableEffect(context, previewViewRef, previewUseCase, imageCapture, imageAnalysis) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                val viewDisplay = previewViewRef?.display ?: return
                if (viewDisplay.displayId == displayId) {
                    val rot = viewDisplay.rotation
                    previewUseCase?.targetRotation = rot
                    imageCapture?.targetRotation = rot
                    imageAnalysis?.targetRotation = rot
                }
            }
        }
        displayManager?.registerDisplayListener(displayListener, null)
        onDispose {
            displayManager?.unregisterDisplayListener(displayListener)
        }
    }

    // Multi-photo picker from within camera
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onPhotosImported(uris, targetDocumentId)
        }
    }

    // Document/File picker from within camera
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesImported(uris, targetDocumentId)
        }
    }

    var showBatchReorderSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Top Controls Bar: Close, Single/Batch Mode, Flash
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
                    .testTag("close_camera_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            // Mode Selector Pill (Single vs Batch)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            ) {
                Row(modifier = Modifier.padding(3.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (!isBatchMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.clickable { isBatchMode = false }
                    ) {
                        Text(
                            text = stringResource(R.string.single_mode),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontWeight = if (!isBatchMode) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isBatchMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.clickable { isBatchMode = true }
                    ) {
                        Text(
                            text = stringResource(R.string.batch_mode),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontWeight = if (isBatchMode) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Flash Toggle
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                        else -> ImageCapture.FLASH_MODE_AUTO
                    }
                    imageCapture?.flashMode = flashMode
                },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
                    .testTag("flash_toggle_button")
            ) {
                Icon(
                    when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                        else -> Icons.Default.FlashAuto
                    },
                    contentDescription = "Flash mode",
                    tint = Color.White
                )
            }
        }

        // 2. Center Camera Viewfinder Workspace (Matched 4:3 Aspect Ratio for 1:1 Preview vs Capture Framing)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                // Dedicated aspect-ratio-matched container so PreviewView and ImageCapture share the exact same FOV
                Box(
                    modifier = Modifier
                        .aspectRatio(
                            ratio = cameraAspectRatio,
                            matchHeightConstraintsFirst = isLandscape
                        )
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                // COMPATIBLE + FIT_CENTER inside the exact-aspect container prevents hidden cropping
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FIT_CENTER
                            }
                            previewViewRef = previewView

                            fun bindCamera() {
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0

                                        val preview = Preview.Builder()
                                            .setResolutionSelector(CameraResolutionConfig.buildPreviewResolutionSelector())
                                            .setTargetRotation(targetRotation)
                                            .build()
                                            .also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }

                                        val capture = ImageCapture.Builder()
                                            .setResolutionSelector(CameraResolutionConfig.buildImageCaptureResolutionSelector())
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                            .setJpegQuality(98)
                                            .setTargetRotation(targetRotation)
                                            .setFlashMode(flashMode)
                                            .build()

                                        val analysis = ImageAnalysis.Builder()
                                            .setResolutionSelector(CameraResolutionConfig.buildImageAnalysisResolutionSelector())
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .setTargetRotation(targetRotation)
                                            .build()

                                        previewUseCase = preview
                                        imageCapture = capture
                                        imageAnalysis = analysis

                                        // Prioritize rear camera's main sensor
                                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                        cameraProvider.unbindAll()

                                        // Build shared ViewPort so Preview, ImageCapture, and ImageAnalysis share identical framing
                                        val viewW = previewView.width.coerceAtLeast(300)
                                        val viewH = previewView.height.coerceAtLeast(400)
                                        val sharedViewPort = previewView.viewPort ?: ViewPort.Builder(
                                            Rational(viewW, viewH),
                                            targetRotation
                                        ).setScaleType(ViewPort.FIT).build()

                                        val boundCamera = try {
                                            val group = UseCaseGroup.Builder()
                                                .setViewPort(sharedViewPort)
                                                .addUseCase(preview)
                                                .addUseCase(capture)
                                                .addUseCase(analysis)
                                                .build()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                group
                                            )
                                        } catch (_: Exception) {
                                            // Fallback to Preview + ImageCapture in UseCaseGroup to guarantee MAXIMUM still resolution
                                            val group = UseCaseGroup.Builder()
                                                .setViewPort(sharedViewPort)
                                                .addUseCase(preview)
                                                .addUseCase(capture)
                                                .build()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                group
                                            )
                                        }

                                        camera = boundCamera

                                        // Ensure consistent 1.0x zoom state across Preview and ImageCapture
                                        boundCamera.cameraControl.setZoomRatio(currentZoomRatio)

                                        // Trigger continuous center auto-focus/metering for sharp document text
                                        val centerPoint = previewView.meteringPointFactory.createPoint(
                                            previewView.width / 2f,
                                            previewView.height / 2f
                                        )
                                        val focusAction = FocusMeteringAction.Builder(
                                            centerPoint,
                                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
                                        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                        boundCamera.cameraControl.startFocusAndMetering(focusAction)

                                        // Read actual resolved still-capture dimensions and aspect ratio
                                        val resInfo = capture.resolutionInfo ?: preview.resolutionInfo
                                        if (resInfo != null) {
                                            val crop = resInfo.cropRect
                                            val rot = resInfo.rotationDegrees
                                            val uprightAspect = CameraResolutionConfig.computeUprightAspectRatio(
                                                crop.width(),
                                                crop.height(),
                                                rot
                                            )
                                            if (uprightAspect > 0.2f && uprightAspect < 5.0f) {
                                                cameraAspectRatio = uprightAspect
                                            }
                                            val fullRes = capture.resolutionInfo?.resolution ?: resInfo.resolution
                                            val mp = (fullRes.width.toLong() * fullRes.height.toLong()) / 1_000_000f
                                            resolutionLabel = String.format(
                                                java.util.Locale.US,
                                                "%.1f MP (%d×%d)",
                                                mp,
                                                max(fullRes.width, fullRes.height),
                                                min(fullRes.width, fullRes.height)
                                            )
                                        }
                                    } catch (_: Exception) {
                                        // Camera bind error fallback
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }

                            // Configure pinch-to-zoom and tap-to-focus on PreviewView so Preview & Capture stay in sync
                            val scaleDetector = ScaleGestureDetector(
                                ctx,
                                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                                        val cam = camera ?: return false
                                        val zoomState = cam.cameraInfo.zoomState.value ?: return false
                                        val newZoom = (zoomState.zoomRatio * detector.scaleFactor).coerceIn(
                                            zoomState.minZoomRatio,
                                            zoomState.maxZoomRatio.coerceAtMost(4.0f)
                                        )
                                        currentZoomRatio = newZoom
                                        cam.cameraControl.setZoomRatio(newZoom)
                                        return true
                                    }
                                }
                            )

                            previewView.setOnTouchListener { v: View, event: MotionEvent ->
                                scaleDetector.onTouchEvent(event)
                                if (event.action == MotionEvent.ACTION_UP && !scaleDetector.isInProgress) {
                                    v.performClick()
                                    val cam = camera
                                    if (cam != null) {
                                        val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                                        val action = FocusMeteringAction.Builder(
                                            point,
                                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
                                        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                        cam.cameraControl.startFocusAndMetering(action)
                                    }
                                }
                                true
                            }

                            previewView.post {
                                bindCamera()
                            }
                            previewView
                        },
                        update = { previewView ->
                            val rot = previewView.display?.rotation ?: Surface.ROTATION_0
                            previewUseCase?.targetRotation = rot
                            imageCapture?.targetRotation = rot
                            imageAnalysis?.targetRotation = rot
                            imageCapture?.flashMode = flashMode
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Scanning Guides Overlay aligned strictly to the exact 4:3 camera preview frame
                    ScanningGuidesOverlay(
                        modifier = Modifier.fillMaxSize()
                    )

                    // High-Resolution & Zoom Indicator Pill
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        resolutionLabel?.let { label ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = "$label • ${(currentZoomRatio * 10f).roundToInt() / 10f}x",
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Camera Permission Needed or Emulator State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.DarkGray,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Camera,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.camera_permission_required),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.testTag("grant_camera_permission_button")
                    ) {
                        Text(stringResource(R.string.grant_permission))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.importSampleDocument(targetDocumentId) },
                        modifier = Modifier.testTag("use_sample_doc_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate Scan with Test Document")
                    }
                }
            }
        }

        // 3. Bottom Capture Controls Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Batch queue indicator with interactive Review & Reorder button
            if (isBatchMode && batchQueue.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .clickable { showBatchReorderSheet = true }
                        .testTag("review_batch_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Review & Reorder (${batchQueue.size} ${if (batchQueue.size == 1) "page" else "pages"})",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Import Buttons (Gallery + PDF/Documents)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .testTag("camera_gallery_button")
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = "Import from Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            documentPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .testTag("camera_document_button")
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = "Import PDF or Document",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Shutter Capture Button (Full-Resolution Still Capture Pipeline)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .background(if (isCapturing) Color.Gray else ScanGuideBlue, CircleShape)
                        .clickable(enabled = !isCapturing) {
                            val capture = imageCapture
                            if (capture != null) {
                                isCapturing = true
                                // Sync targetRotation right before still capture
                                previewViewRef?.display?.rotation?.let { rot ->
                                    capture.targetRotation = rot
                                }

                                val scansDir = File(context.filesDir, "scans").apply { if (!exists()) mkdirs() }
                                val photoFile = File(scansDir, "capture_${System.currentTimeMillis()}.jpg")
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                                val visibleAspect = cameraAspectRatio
                                val executor = ContextCompat.getMainExecutor(context)
                                capture.takePicture(
                                    outputOptions,
                                    executor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            isCapturing = false
                                            viewModel.onPhotoCaptured(
                                                path = photoFile.absolutePath,
                                                isBatch = isBatchMode,
                                                targetDocumentId = targetDocumentId,
                                                previewAspectRatio = visibleAspect
                                            )
                                        }

                                        override fun onError(exc: ImageCaptureException) {
                                            isCapturing = false
                                            Toast.makeText(
                                                context,
                                                "Capture error: ${exc.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            } else {
                                // Fallback simulation for emulators without camera hardware
                                viewModel.importSampleDocument(targetDocumentId)
                            }
                        }
                        .testTag("shutter_capture_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.White, CircleShape)
                    )
                }

                // Finish / Review button for batch mode, or Sample document action
                if (isBatchMode && batchQueue.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.finishBatchCapturing(targetDocumentId) },
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .testTag("finish_batch_button")
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Finish Batch",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.importSampleDocument(targetDocumentId) },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .testTag("quick_test_doc_button")
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Sample Document",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        if (showBatchReorderSheet && batchQueue.isNotEmpty()) {
            BatchReorderBottomSheet(
                batchQueue = batchQueue,
                onDismiss = { showBatchReorderSheet = false },
                onMoveUp = { idx: Int -> viewModel.reorderBatchQueue(idx, idx - 1) },
                onMoveDown = { idx: Int -> viewModel.reorderBatchQueue(idx, idx + 1) },
                onDelete = { idx: Int -> viewModel.removeBatchItem(idx) },
                onProceed = {
                    showBatchReorderSheet = false
                    viewModel.finishBatchCapturing(targetDocumentId)
                }
            )
        }
    }
}

@Composable
fun ScanningGuidesOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_sweep")
    val laserYRatio by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val left = w * 0.06f
        val right = w * 0.94f
        val top = h * 0.06f
        val bottom = h * 0.94f

        val bracketLength = 36.dp.toPx()
        val strokeWidth = 3.5.dp.toPx()

        // 4 Blue Corner Guides aligned to the active 4:3 camera preview viewport
        val cornerPath = Path().apply {
            // Top Left
            moveTo(left, top + bracketLength)
            lineTo(left, top)
            lineTo(left + bracketLength, top)

            // Top Right
            moveTo(right - bracketLength, top)
            lineTo(right, top)
            lineTo(right, top + bracketLength)

            // Bottom Right
            moveTo(right, bottom - bracketLength)
            lineTo(right, bottom)
            lineTo(right - bracketLength, bottom)

            // Bottom Left
            moveTo(left + bracketLength, bottom)
            lineTo(left, bottom)
            lineTo(left, bottom - bracketLength)
        }

        drawPath(
            path = cornerPath,
            color = ScanGuideBlue,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Subtle guide frame rectangle
        drawRect(
            color = ScanGuideBlue.copy(alpha = 0.15f),
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            style = Stroke(width = 1.dp.toPx())
        )

        // Laser scan beam
        val currentLaserY = top + (bottom - top) * laserYRatio
        drawLine(
            color = ScanLaserLine,
            start = androidx.compose.ui.geometry.Offset(left + 8f, currentLaserY),
            end = androidx.compose.ui.geometry.Offset(right - 8f, currentLaserY),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchReorderBottomSheet(
    batchQueue: List<String>,
    onDismiss: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onProceed: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        text = "${batchQueue.size} captured pages in queue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onProceed,
                    modifier = Modifier.testTag("batch_proceed_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Proceed to Crop")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(batchQueue, key = { index, path -> "$path-$index" }) { index, path ->
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
                            Box(
                                modifier = Modifier
                                    .size(54.dp, 72.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.LightGray)
                            ) {
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = "Page ${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            // Move Up
                            IconButton(
                                onClick = { onMoveUp(index) },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                            }

                            // Move Down
                            IconButton(
                                onClick = { onMoveDown(index) },
                                enabled = index < batchQueue.size - 1
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                            }

                            // Delete
                            IconButton(
                                onClick = { onDelete(index) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
