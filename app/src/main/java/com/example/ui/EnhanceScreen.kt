package com.example.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.processor.Corners
import com.example.processor.DocumentProcessor
import com.example.processor.ManualAdjustments
import kotlin.math.roundToInt

data class FilterOption(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val badge: String? = null
)

enum class AdjustCategory(val title: String) {
    QUICK("Quick"),
    LIGHT("Light & Tone"),
    COLOR("Color Balance"),
    DETAILS("Sharpness & Details")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhanceScreen(
    croppedBitmap: Bitmap,
    originalImagePath: String,
    targetDocumentId: Long?,
    pageId: Long?,
    corners: Corners,
    isBatchRemaining: Boolean,
    onBack: () -> Unit,
    onDone: (filterMode: String, brightness: Float, contrast: Float, rotationDegrees: Int, manual: ManualAdjustments) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBack()
    }

    var selectedFilter by remember { mutableStateOf("ENHANCED") }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var isComparingBefore by remember { mutableStateOf(false) }

    // Manual adjustment parameters
    var lightness by remember { mutableFloatStateOf(0f) }           // -1.0 .. 1.0
    var contrast by remember { mutableFloatStateOf(1.0f) }          // 0.5 .. 2.0
    var saturation by remember { mutableFloatStateOf(1.0f) }        // 0.0 .. 2.5
    var temperature by remember { mutableFloatStateOf(0f) }         // -1.0 .. 1.0
    var warmth by remember { mutableFloatStateOf(0f) }              // -1.0 .. 1.0
    var tint by remember { mutableFloatStateOf(0f) }                // -1.0 .. 1.0
    var hue by remember { mutableFloatStateOf(0f) }                 // -180 .. 180
    var highlights by remember { mutableFloatStateOf(0f) }          // -1.0 .. 1.0
    var shadows by remember { mutableFloatStateOf(0f) }             // -1.0 .. 1.0
    var sharpness by remember { mutableFloatStateOf(0.5f) }         // 0.0 .. 2.0
    var fade by remember { mutableFloatStateOf(0f) }                // 0.0 .. 1.0
    var vignette by remember { mutableFloatStateOf(0f) }            // -1.0 .. 1.0

    // Zoom on result preview state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    var showFullScreenZoom by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf(AdjustCategory.QUICK) }

    val currentManual = remember(
        lightness, contrast, saturation, temperature, warmth, tint, hue,
        highlights, shadows, sharpness, fade, vignette
    ) {
        ManualAdjustments(
            lightness = lightness,
            contrast = contrast,
            saturation = saturation,
            temperature = temperature,
            warmth = warmth,
            tint = tint,
            hue = hue,
            highlights = highlights,
            shadows = shadows,
            sharpness = sharpness,
            fade = fade,
            vignette = vignette
        )
    }

    // Keep interactive live preview fast on 12MP-16MP scans while preserving full-res croppedBitmap for final save
    val previewSourceBitmap = remember(croppedBitmap) {
        val maxPreviewDim = 1440
        val w = croppedBitmap.width
        val h = croppedBitmap.height
        if (w > maxPreviewDim || h > maxPreviewDim) {
            val scale = maxPreviewDim.toFloat() / kotlin.math.max(w, h).toFloat()
            val pw = (w * scale).roundToInt().coerceAtLeast(100)
            val ph = (h * scale).roundToInt().coerceAtLeast(100)
            Bitmap.createScaledBitmap(croppedBitmap, pw, ph, true)
        } else {
            croppedBitmap
        }
    }

    // Live processed bitmap preview
    val previewBitmap = remember(selectedFilter, rotationDegrees, currentManual, previewSourceBitmap) {
        DocumentProcessor.applyEnhancements(
            source = previewSourceBitmap,
            filterMode = selectedFilter,
            brightness = lightness,
            contrast = contrast,
            rotationDegrees = rotationDegrees,
            manual = currentManual
        )
    }

    val filterOptions = listOf(
        FilterOption("ENHANCED", "Magic Color", Icons.Default.AutoAwesome, "Auto"),
        FilterOption("DOCUMENT", "Document", Icons.Default.Tune),
        FilterOption("GRAYSCALE", "Grayscale", Icons.Default.InvertColors),
        FilterOption("BW", "B&W", Icons.Default.Filter),
        FilterOption("ORIGINAL", "Original", Icons.Default.Image)
    )

    fun resetAllAdjustments() {
        lightness = 0f
        contrast = 1.0f
        saturation = 1.0f
        temperature = 0f
        warmth = 0f
        tint = 0f
        hue = 0f
        highlights = 0f
        shadows = 0f
        sharpness = 0.5f
        fade = 0f
        vignette = 0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Enhance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isComparingBefore) "Original (Before)" else "Enhanced (After)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isComparingBefore) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("enhance_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Before / After Compare Button
                OutlinedButton(
                    onClick = { isComparingBefore = !isComparingBefore },
                    colors = if (isComparingBefore) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.outlinedButtonColors(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("compare_before_after_button")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isComparingBefore) "Before" else "Compare",
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Zoom Button in TopAppBar
                IconButton(
                    onClick = { showFullScreenZoom = true },
                    modifier = Modifier.testTag("enhance_zoom_button")
                ) {
                    Icon(
                        Icons.Default.ZoomIn,
                        contentDescription = stringResource(R.string.zoom_in)
                    )
                }

                // Rotate Button
                IconButton(
                    onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                    modifier = Modifier.testTag("rotate_scan_button")
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90 degrees")
                }

                // Done / Save Button
                Button(
                    onClick = {
                        onDone(selectedFilter, lightness, contrast, rotationDegrees, currentManual)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .testTag("save_enhancement_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.done),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Center Live Preview of the enhanced or original document
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp,
                color = Color.White,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                            val maxOffsetX = 400f * (newScale - 1f)
                            val maxOffsetY = 500f * (newScale - 1f)
                            val newOffset = if (newScale > 1.05f) {
                                Offset(
                                    (zoomOffset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    (zoomOffset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            } else {
                                Offset.Zero
                            }
                            zoomScale = newScale
                            zoomOffset = newOffset
                        }
                    }
                    .pointerInput(zoomScale) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (zoomScale > 1.1f) {
                                    zoomScale = 1f
                                    zoomOffset = Offset.Zero
                                } else {
                                    zoomScale = 2.5f
                                }
                            },
                            onPress = {
                                if (zoomScale <= 1.05f) {
                                    isComparingBefore = true
                                    tryAwaitRelease()
                                    isComparingBefore = false
                                }
                            }
                        )
                    }
            ) {
                val displayBitmap = if (isComparingBefore) croppedBitmap else previewBitmap
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = "Document Scan Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = zoomOffset.x,
                                translationY = zoomOffset.y
                            )
                    )
                }
            }

            // Floating indicator tag
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isComparingBefore) Color(0xCCB71C1C) else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = if (isComparingBefore) "HOLDING: ORIGINAL" else if (zoomScale > 1.05f) "PINCH & PAN ACTIVE" else "TAP & HOLD TO COMPARE",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Floating Zoom Controls overlay on Preview
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (zoomScale > 1.05f) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier.clickable {
                            zoomScale = 1f
                            zoomOffset = Offset.Zero
                        }
                    ) {
                        Text(
                            text = "${(zoomScale * 100).roundToInt()}% • Reset",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { showFullScreenZoom = true }
                        .testTag("fullscreen_zoom_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Fullscreen,
                            contentDescription = "Full Screen Zoom",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Bottom Controls: Filters, Tabs, and Sliders
        Surface(
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. One-Tap Automatic Filter Selector Pills
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(filterOptions) { filter ->
                        val isSelected = selectedFilter == filter.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedFilter = filter.id }
                                .testTag("filter_tab_${filter.id.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = filter.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = filter.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (filter.badge != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "★",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Adjustment Category Tabs Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedCategory.ordinal,
                        edgePadding = 0.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        AdjustCategory.values().forEach { cat ->
                            Tab(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                text = { Text(cat.title, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (!currentManual.isDefault()) {
                        TextButton(
                            onClick = { resetAllAdjustments() },
                            modifier = Modifier.testTag("reset_all_adjustments_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Reset All", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Category-specific Sliders
                when (selectedCategory) {
                    AdjustCategory.QUICK -> {
                        AdjustmentSliderRow(
                            name = "Lightness",
                            value = lightness,
                            valueRange = -0.5f..0.5f,
                            defaultValue = 0f,
                            format = { "${(it * 100).roundToInt()}" },
                            onValueChange = { lightness = it },
                            onReset = { lightness = 0f }
                        )
                        AdjustmentSliderRow(
                            name = "Contrast",
                            value = contrast,
                            valueRange = 0.6f..1.8f,
                            defaultValue = 1.0f,
                            format = { "${(it * 100).roundToInt()}%" },
                            onValueChange = { contrast = it },
                            onReset = { contrast = 1.0f }
                        )
                    }

                    AdjustCategory.LIGHT -> {
                        AdjustmentSliderRow(
                            name = "Lightness",
                            value = lightness,
                            valueRange = -0.5f..0.5f,
                            defaultValue = 0f,
                            format = { "${(it * 100).roundToInt()}" },
                            onValueChange = { lightness = it },
                            onReset = { lightness = 0f }
                        )
                        AdjustmentSliderRow(
                            name = "Contrast",
                            value = contrast,
                            valueRange = 0.6f..1.8f,
                            defaultValue = 1.0f,
                            format = { "${(it * 100).roundToInt()}%" },
                            onValueChange = { contrast = it },
                            onReset = { contrast = 1.0f }
                        )
                        AdjustmentSliderRow(
                            name = "Highlights",
                            value = highlights,
                            valueRange = -1.0f..1.0f,
                            defaultValue = 0f,
                            format = { "${(it * 100).roundToInt()}" },
                            onValueChange = { highlights = it },
                            onReset = { highlights = 0f }
                        )
                        AdjustmentSliderRow(
                            name = "Shadows",
                            value = shadows,
                            valueRange = -1.0f..1.0f,
                            defaultValue = 0f,
                            format = { "${(it * 100).roundToInt()}" },
                            onValueChange = { shadows = it },
                            onReset = { shadows = 0f }
                        )
                        AdjustmentSliderRow(
                            name = "Fade",
                            value = fade,
                            valueRange = 0.0f..1.0f,
                            defaultValue = 0f,
                            format = { "${(it * 100).roundToInt()}%" },
                            onValueChange = { fade = it },
                            onReset = { fade = 0f }
                        )
                    }

                    AdjustCategory.COLOR -> {
                        AdjustmentSliderRow(
                            name = "Saturation",
                            value = saturation,
                            valueRange = 0.0f..2.5f,
                            defaultValue = 1.0f,
                            format = { "${(it * 100).roundToInt()}%" },
                            onValueChange = { saturation = it },
                            onReset = { saturation = 1.0f }
                        )
                        AdjustmentSliderRow(
                            name = "Temperature",
                            value = temperature,
                            valueRange = -1.0f..1.0f,
                            defaultValue = 0f,
                            format = { if (it < 0) "${(it * 100).roundToInt()} Cool" else if (it > 0) "+${(it * 100).roundToInt()} Warm" else "0" },
                            onValueChange = { temperature = it },
                            onReset = { temperature = 0f }
                        )
                        AdjustmentSliderRow(
                            name = "Warmth",
                            value = warmth,
                            valueRange = -1.0f..1.0f,
                            defaultValue = 0f,
                            format = { "${(it * 100).roundToInt()}" },
                            onValueChange = { warmth = it },
                            onReset = { warmth = 0f }
                        )
                        AdjustmentSliderRow(
                            name = "Tint",
                            value = tint,
                            valueRange = -1.0f..1.0f,
                            defaultValue = 0f,
                            format = { if (it < 0) "${(it * 100).roundToInt()} Green" else if (it > 0) "+${(it * 100).roundToInt()} Magenta" else "0" },
                            onValueChange = { tint = it },
                            onReset = { tint = 0f }
                        )
                        AdjustmentSliderRow(
                            name = "Hue",
                            value = hue,
                            valueRange = -180f..180f,
                            defaultValue = 0f,
                            format = { "${it.roundToInt()}°" },
                            onValueChange = { hue = it },
                            onReset = { hue = 0f }
                        )
                    }

                    AdjustCategory.DETAILS -> {
                        AdjustmentSliderRow(
                            name = "Sharpness",
                            value = sharpness,
                            valueRange = 0.0f..2.0f,
                            defaultValue = 0.5f,
                            format = { "%.1fx".format(it) },
                            onValueChange = { sharpness = it },
                            onReset = { sharpness = 0.5f }
                        )
                        AdjustmentSliderRow(
                            name = "Vignette",
                            value = vignette,
                            valueRange = -1.0f..1.0f,
                            defaultValue = 0f,
                            format = { "${(it * 100).roundToInt()}" },
                            onValueChange = { vignette = it },
                            onReset = { vignette = 0f }
                        )
                    }
                }
            }
        }

        if (showFullScreenZoom) {
            val displayBitmap = if (isComparingBefore) croppedBitmap else previewBitmap
            FullScreenZoomDialog(
                bitmap = displayBitmap,
                onDismiss = { showFullScreenZoom = false }
            )
        }
    }
}

/**
 * Reusable adjustment slider row with numeric display badge and individual reset button.
 */
@Composable
private fun AdjustmentSliderRow(
    name: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isModified = kotlin.math.abs(value - defaultValue) > 0.01f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Setting label
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isModified) FontWeight.Bold else FontWeight.Normal,
            color = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(84.dp)
        )

        // Slider
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .weight(1f)
                .testTag("slider_${name.lowercase()}")
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Numeric Badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isModified) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(58.dp)
        ) {
            Text(
                text = format(value),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }

        // Individual Reset Button
        if (isModified) {
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reset $name",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(32.dp))
        }
    }
}

/**
 * Fullscreen precision zoom dialog allowing pinch-to-zoom up to 8x and fluid pan inspection.
 */
@Composable
fun FullScreenZoomDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            val maxOffsetX = 600f * (scale - 1f)
                            val maxOffsetY = 800f * (scale - 1f)
                            offset = if (scale > 1.05f) {
                                Offset(
                                    (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            } else Offset.Zero
                        }
                    }
                    .pointerInput(scale) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 3f
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Full zoom view",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }

            // Top controls: Scale indicator & Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = "Zoom: ${(scale * 100).roundToInt()}% (Double-tap or pinch)",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Bottom quick zoom controls (+ / - / Reset)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    scale = (scale - 0.5f).coerceAtLeast(1f)
                    if (scale <= 1.05f) offset = Offset.Zero
                }) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                }

                TextButton(onClick = {
                    scale = 1f
                    offset = Offset.Zero
                }) {
                    Text("100% Fit", color = Color.White, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = {
                    scale = (scale + 0.5f).coerceAtMost(8f)
                }) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                }
            }
        }
    }
}
