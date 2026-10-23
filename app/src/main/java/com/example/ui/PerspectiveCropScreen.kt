package com.example.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.processor.CornerPoint
import com.example.processor.Corners
import com.example.processor.DocumentProcessor
import com.example.ui.components.MagnifierLoupe
import com.example.ui.theme.ScanGuideActiveHandle
import com.example.ui.theme.ScanGuideBlue
import com.example.ui.theme.ScanGuideOverlayFill
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CropDragTarget {
    NONE,
    CORNER_TL, CORNER_TR, CORNER_BR, CORNER_BL,
    EDGE_TOP, EDGE_RIGHT, EDGE_BOTTOM, EDGE_LEFT,
    BODY_CENTER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerspectiveCropScreen(
    originalImagePath: String,
    targetDocumentId: Long?,
    pageId: Long?,
    isBatchRemaining: Boolean,
    onBack: () -> Unit,
    onProceed: (Corners) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBack()
    }

    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var corners by remember { mutableStateOf<Corners?>(null) }
    var initialDetectedCorners by remember { mutableStateOf<Corners?>(null) }

    var activeDragTarget by remember { mutableStateOf(CropDragTarget.NONE) }
    var dragTouchOffset by remember { mutableStateOf(Offset.Zero) }

    var isManuallyAdjusted by remember { mutableStateOf(false) }

    // Always keep an updated reference to current corners to prevent closure capturing issues
    val latestCorners by rememberUpdatedState(corners)

    LaunchedEffect(originalImagePath) {
        if (loadedBitmap == null) {
            val bmp = DocumentProcessor.loadBitmapFromFile(originalImagePath)
            loadedBitmap = bmp
            if (bmp != null) {
                val detected = DocumentProcessor.detectCorners(bmp)
                corners = detected
                initialDetectedCorners = detected
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101620))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.perspective_crop_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isManuallyAdjusted) Color(0xFFE65100) else Color(0xFF00897B)
                        ) {
                            Text(
                                text = if (isManuallyAdjusted) "Manual Lock" else "Auto (${((corners?.confidence ?: 0.9f) * 100).roundToInt()}%)",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.drag_edges_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("crop_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            actions = {
                Button(
                    onClick = {
                        corners?.let { onProceed(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("crop_proceed_button")
                ) {
                    Text(stringResource(R.string.next), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        // Interactive Crop Area
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = loadedBitmap
            val currentCorners = corners

            if (bmp != null && !bmp.isRecycled && currentCorners != null) {
                val containerW = maxWidth.value.coerceAtLeast(10f)
                val containerH = maxHeight.value.coerceAtLeast(10f)

                val bmpW = bmp.width.toFloat().coerceAtLeast(1f)
                val bmpH = bmp.height.toFloat().coerceAtLeast(1f)

                val scale = min(containerW / bmpW, containerH / bmpH)
                val fitW = (bmpW * scale).coerceAtLeast(10f)
                val fitH = (bmpH * scale).coerceAtLeast(10f)

                val density = LocalDensity.current
                val fitWPx = with(density) { fitW.dp.toPx() }.coerceAtLeast(10f)
                val fitHPx = with(density) { fitH.dp.toPx() }.coerceAtLeast(10f)

                // Box with exact fit dimensions
                Box(
                    modifier = Modifier
                        .size(fitW.dp, fitH.dp)
                        .pointerInput(bmp) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    dragTouchOffset = startOffset
                                    val cur = latestCorners ?: return@detectDragGestures

                                    val viewW = size.width.toFloat().coerceAtLeast(1f)
                                    val viewH = size.height.toFloat().coerceAtLeast(1f)

                                    // Touch position in bitmap space
                                    val touchBmpX = ((startOffset.x / viewW) * bmpW).coerceIn(0f, bmpW)
                                    val touchBmpY = ((startOffset.y / viewH) * bmpH).coerceIn(0f, bmpH)

                                    // Touch radii in bitmap coordinates
                                    val cornerHitRadius = ((42.dp.toPx() / viewW) * bmpW).coerceAtLeast(20f)
                                    val edgeHitRadius = ((36.dp.toPx() / viewW) * bmpW).coerceAtLeast(20f)

                                    // 1. Check 4 corners first
                                    val dTL = hypot(touchBmpX - cur.topLeft.x, touchBmpY - cur.topLeft.y)
                                    val dTR = hypot(touchBmpX - cur.topRight.x, touchBmpY - cur.topRight.y)
                                    val dBR = hypot(touchBmpX - cur.bottomRight.x, touchBmpY - cur.bottomRight.y)
                                    val dBL = hypot(touchBmpX - cur.bottomLeft.x, touchBmpY - cur.bottomLeft.y)

                                    val minCornerDist = minOf(dTL, dTR, dBR, dBL)
                                    if (minCornerDist <= cornerHitRadius) {
                                        activeDragTarget = when (minCornerDist) {
                                            dTL -> CropDragTarget.CORNER_TL
                                            dTR -> CropDragTarget.CORNER_TR
                                            dBR -> CropDragTarget.CORNER_BR
                                            else -> CropDragTarget.CORNER_BL
                                        }
                                        return@detectDragGestures
                                    }

                                    // 2. Check 4 edge midpoints
                                    val topMidX = (cur.topLeft.x + cur.topRight.x) / 2f
                                    val topMidY = (cur.topLeft.y + cur.topRight.y) / 2f
                                    val rightMidX = (cur.topRight.x + cur.bottomRight.x) / 2f
                                    val rightMidY = (cur.topRight.y + cur.bottomRight.y) / 2f
                                    val bottomMidX = (cur.bottomLeft.x + cur.bottomRight.x) / 2f
                                    val bottomMidY = (cur.bottomLeft.y + cur.bottomRight.y) / 2f
                                    val leftMidX = (cur.topLeft.x + cur.bottomLeft.x) / 2f
                                    val leftMidY = (cur.topLeft.y + cur.bottomLeft.y) / 2f

                                    val dTop = hypot(touchBmpX - topMidX, touchBmpY - topMidY)
                                    val dRight = hypot(touchBmpX - rightMidX, touchBmpY - rightMidY)
                                    val dBottom = hypot(touchBmpX - bottomMidX, touchBmpY - bottomMidY)
                                    val dLeft = hypot(touchBmpX - leftMidX, touchBmpY - leftMidY)

                                    val minEdgeDist = minOf(dTop, dRight, dBottom, dLeft)
                                    if (minEdgeDist <= edgeHitRadius) {
                                        activeDragTarget = when (minEdgeDist) {
                                            dTop -> CropDragTarget.EDGE_TOP
                                            dRight -> CropDragTarget.EDGE_RIGHT
                                            dBottom -> CropDragTarget.EDGE_BOTTOM
                                            else -> CropDragTarget.EDGE_LEFT
                                        }
                                        return@detectDragGestures
                                    }

                                    // 3. Check if inside quadrilateral for whole-body moving
                                    val inside = isPointInQuad(
                                        touchBmpX, touchBmpY,
                                        cur.topLeft, cur.topRight, cur.bottomRight, cur.bottomLeft
                                    )
                                    if (inside) {
                                        activeDragTarget = CropDragTarget.BODY_CENTER
                                    } else {
                                        activeDragTarget = CropDragTarget.NONE
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragTouchOffset += dragAmount
                                    val cur = latestCorners ?: return@detectDragGestures
                                    if (activeDragTarget != CropDragTarget.NONE) {
                                        isManuallyAdjusted = true
                                    }

                                    val viewW = size.width.toFloat().coerceAtLeast(1f)
                                    val viewH = size.height.toFloat().coerceAtLeast(1f)
                                    if (dragAmount.x.isNaN() || dragAmount.y.isNaN()) return@detectDragGestures

                                    // Delta in bitmap coordinates
                                    val deltaBmpX = (dragAmount.x / viewW) * bmpW
                                    val deltaBmpY = (dragAmount.y / viewH) * bmpH
                                    if (deltaBmpX.isNaN() || deltaBmpY.isNaN() || deltaBmpX.isInfinite() || deltaBmpY.isInfinite()) return@detectDragGestures

                                    corners = try {
                                        when (activeDragTarget) {
                                            CropDragTarget.CORNER_TL -> {
                                                val newX = (cur.topLeft.x + deltaBmpX).coerceIn(0f, bmpW)
                                                val newY = (cur.topLeft.y + deltaBmpY).coerceIn(0f, bmpH)
                                                cur.copy(topLeft = CornerPoint(newX, newY))
                                            }
                                            CropDragTarget.CORNER_TR -> {
                                                val newX = (cur.topRight.x + deltaBmpX).coerceIn(0f, bmpW)
                                                val newY = (cur.topRight.y + deltaBmpY).coerceIn(0f, bmpH)
                                                cur.copy(topRight = CornerPoint(newX, newY))
                                            }
                                            CropDragTarget.CORNER_BR -> {
                                                val newX = (cur.bottomRight.x + deltaBmpX).coerceIn(0f, bmpW)
                                                val newY = (cur.bottomRight.y + deltaBmpY).coerceIn(0f, bmpH)
                                                cur.copy(bottomRight = CornerPoint(newX, newY))
                                            }
                                            CropDragTarget.CORNER_BL -> {
                                                val newX = (cur.bottomLeft.x + deltaBmpX).coerceIn(0f, bmpW)
                                                val newY = (cur.bottomLeft.y + deltaBmpY).coerceIn(0f, bmpH)
                                                cur.copy(bottomLeft = CornerPoint(newX, newY))
                                            }
                                            CropDragTarget.EDGE_TOP -> {
                                                val newTL_Y = (cur.topLeft.y + deltaBmpY).coerceIn(0f, bmpH)
                                                val newTR_Y = (cur.topRight.y + deltaBmpY).coerceIn(0f, bmpH)
                                                val newTL_X = (cur.topLeft.x + deltaBmpX).coerceIn(0f, bmpW)
                                                val newTR_X = (cur.topRight.x + deltaBmpX).coerceIn(0f, bmpW)
                                                cur.copy(
                                                    topLeft = CornerPoint(newTL_X, newTL_Y),
                                                    topRight = CornerPoint(newTR_X, newTR_Y)
                                                )
                                            }
                                            CropDragTarget.EDGE_BOTTOM -> {
                                                val newBL_Y = (cur.bottomLeft.y + deltaBmpY).coerceIn(0f, bmpH)
                                                val newBR_Y = (cur.bottomRight.y + deltaBmpY).coerceIn(0f, bmpH)
                                                val newBL_X = (cur.bottomLeft.x + deltaBmpX).coerceIn(0f, bmpW)
                                                val newBR_X = (cur.bottomRight.x + deltaBmpX).coerceIn(0f, bmpW)
                                                cur.copy(
                                                    bottomLeft = CornerPoint(newBL_X, newBL_Y),
                                                    bottomRight = CornerPoint(newBR_X, newBR_Y)
                                                )
                                            }
                                            CropDragTarget.EDGE_LEFT -> {
                                                val newTL_X = (cur.topLeft.x + deltaBmpX).coerceIn(0f, bmpW)
                                                val newBL_X = (cur.bottomLeft.x + deltaBmpX).coerceIn(0f, bmpW)
                                                val newTL_Y = (cur.topLeft.y + deltaBmpY).coerceIn(0f, bmpH)
                                                val newBL_Y = (cur.bottomLeft.y + deltaBmpY).coerceIn(0f, bmpH)
                                                cur.copy(
                                                    topLeft = CornerPoint(newTL_X, newTL_Y),
                                                    bottomLeft = CornerPoint(newBL_X, newBL_Y)
                                                )
                                            }
                                            CropDragTarget.EDGE_RIGHT -> {
                                                val newTR_X = (cur.topRight.x + deltaBmpX).coerceIn(0f, bmpW)
                                                val newBR_X = (cur.bottomRight.x + deltaBmpX).coerceIn(0f, bmpW)
                                                val newTR_Y = (cur.topRight.y + deltaBmpY).coerceIn(0f, bmpH)
                                                val newBR_Y = (cur.bottomRight.y + deltaBmpY).coerceIn(0f, bmpH)
                                                cur.copy(
                                                    topRight = CornerPoint(newTR_X, newTR_Y),
                                                    bottomRight = CornerPoint(newBR_X, newBR_Y)
                                                )
                                            }
                                            CropDragTarget.BODY_CENTER -> {
                                                val minX = minOf(cur.topLeft.x, cur.bottomLeft.x, cur.topRight.x, cur.bottomRight.x)
                                                val maxX = maxOf(cur.topLeft.x, cur.bottomLeft.x, cur.topRight.x, cur.bottomRight.x)
                                                val minY = minOf(cur.topLeft.y, cur.bottomLeft.y, cur.topRight.y, cur.bottomRight.y)
                                                val maxY = maxOf(cur.topLeft.y, cur.bottomLeft.y, cur.topRight.y, cur.bottomRight.y)

                                                val safeDx = if (deltaBmpX < 0) max(deltaBmpX, -minX) else min(deltaBmpX, max(0f, bmpW - maxX))
                                                val safeDy = if (deltaBmpY < 0) max(deltaBmpY, -minY) else min(deltaBmpY, max(0f, bmpH - maxY))

                                                cur.copy(
                                                    topLeft = CornerPoint((cur.topLeft.x + safeDx).coerceIn(0f, bmpW), (cur.topLeft.y + safeDy).coerceIn(0f, bmpH)),
                                                    topRight = CornerPoint((cur.topRight.x + safeDx).coerceIn(0f, bmpW), (cur.topRight.y + safeDy).coerceIn(0f, bmpH)),
                                                    bottomRight = CornerPoint((cur.bottomRight.x + safeDx).coerceIn(0f, bmpW), (cur.bottomRight.y + safeDy).coerceIn(0f, bmpH)),
                                                    bottomLeft = CornerPoint((cur.bottomLeft.x + safeDx).coerceIn(0f, bmpW), (cur.bottomLeft.y + safeDy).coerceIn(0f, bmpH))
                                                )
                                            }
                                            CropDragTarget.NONE -> cur
                                        }
                                    } catch (_: Throwable) {
                                        cur
                                    }
                                },
                                onDragEnd = {
                                    activeDragTarget = CropDragTarget.NONE
                                },
                                onDragCancel = {
                                    activeDragTarget = CropDragTarget.NONE
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (bmp.isRecycled) return@Canvas
                        val viewW = size.width
                        val viewH = size.height
                        if (viewW <= 0f || viewH <= 0f || bmpW <= 0f || bmpH <= 0f) return@Canvas

                        try {
                            // 1. Draw source bitmap
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstSize = IntSize(viewW.toInt(), viewH.toInt())
                            )

                            // 2. Convert corner coordinates to view space safely
                            val tlX = ((currentCorners.topLeft.x / bmpW) * viewW).coerceIn(0f, viewW)
                            val tlY = ((currentCorners.topLeft.y / bmpH) * viewH).coerceIn(0f, viewH)
                            val trX = ((currentCorners.topRight.x / bmpW) * viewW).coerceIn(0f, viewW)
                            val trY = ((currentCorners.topRight.y / bmpH) * viewH).coerceIn(0f, viewH)
                            val brX = ((currentCorners.bottomRight.x / bmpW) * viewW).coerceIn(0f, viewW)
                            val brY = ((currentCorners.bottomRight.y / bmpH) * viewH).coerceIn(0f, viewH)
                            val blX = ((currentCorners.bottomLeft.x / bmpW) * viewW).coerceIn(0f, viewW)
                            val blY = ((currentCorners.bottomLeft.y / bmpH) * viewH).coerceIn(0f, viewH)

                            val tl = Offset(tlX, tlY)
                            val tr = Offset(trX, trY)
                            val br = Offset(brX, brY)
                            val bl = Offset(blX, blY)

                            // Edge midpoints
                            val topMid = Offset((tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f)
                            val rightMid = Offset((tr.x + br.x) / 2f, (tr.y + br.y) / 2f)
                            val bottomMid = Offset((bl.x + br.x) / 2f, (bl.y + br.y) / 2f)
                            val leftMid = Offset((tl.x + bl.x) / 2f, (tl.y + bl.y) / 2f)

                            // Center midpoint
                            val centerMid = Offset((tl.x + tr.x + br.x + bl.x) / 4f, (tl.y + tr.y + br.y + bl.y) / 4f)

                            // 3. Draw polygon interior tint
                            val polyPath = Path().apply {
                                moveTo(tl.x, tl.y)
                                lineTo(tr.x, tr.y)
                                lineTo(br.x, br.y)
                                lineTo(bl.x, bl.y)
                                close()
                            }
                            drawPath(polyPath, color = ScanGuideOverlayFill)

                            // 4. Draw polygon perimeter lines
                            drawPath(
                                path = polyPath,
                                color = ScanGuideBlue,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )

                            // 5. Draw 4 Edge Midpoint Handles (capsules/bars)
                            listOf(
                                topMid to (activeDragTarget == CropDragTarget.EDGE_TOP),
                                rightMid to (activeDragTarget == CropDragTarget.EDGE_RIGHT),
                                bottomMid to (activeDragTarget == CropDragTarget.EDGE_BOTTOM),
                                leftMid to (activeDragTarget == CropDragTarget.EDGE_LEFT)
                            ).forEach { (offset, isActive) ->
                                val handleColor = if (isActive) ScanGuideActiveHandle else Color.White
                                drawCircle(
                                    color = ScanGuideBlue,
                                    radius = 9.dp.toPx(),
                                    center = offset
                                )
                                drawCircle(
                                    color = handleColor,
                                    radius = 5.dp.toPx(),
                                    center = offset
                                )
                            }

                            // 6. Draw 4 Corner Handles (circles with glow)
                            listOf(
                                tl to (activeDragTarget == CropDragTarget.CORNER_TL),
                                tr to (activeDragTarget == CropDragTarget.CORNER_TR),
                                br to (activeDragTarget == CropDragTarget.CORNER_BR),
                                bl to (activeDragTarget == CropDragTarget.CORNER_BL)
                            ).forEach { (offset, isActive) ->
                                drawCircle(
                                    color = if (isActive) ScanGuideActiveHandle else ScanGuideBlue,
                                    radius = if (isActive) 15.dp.toPx() else 12.dp.toPx(),
                                    center = offset
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 6.dp.toPx(),
                                    center = offset
                                )
                            }

                            // 7. Draw center reposition guide if body is dragged
                            if (activeDragTarget == CropDragTarget.BODY_CENTER) {
                                drawCircle(
                                    color = ScanGuideActiveHandle.copy(alpha = 0.8f),
                                    radius = 18.dp.toPx(),
                                    center = centerMid
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 6.dp.toPx(),
                                    center = centerMid
                                )
                            }
                        } catch (_: Throwable) {
                            // Guard against unexpected Compose canvas drawing exceptions
                        }
                    }

                    // Floating Magnifier Loupe when dragging a corner or edge
                    if (activeDragTarget != CropDragTarget.NONE && activeDragTarget != CropDragTarget.BODY_CENTER && !bmp.isRecycled) {
                        MagnifierLoupe(
                            bitmap = bmp,
                            touchX = dragTouchOffset.x,
                            touchY = dragTouchOffset.y,
                            viewWidth = fitWPx,
                            viewHeight = fitHPx
                        )
                    }
                }
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Bottom Controls: Auto Detect, Full Page, Reset
        Surface(
            color = Color(0xFF181F2A),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        loadedBitmap?.let { bmp ->
                            val detected = DocumentProcessor.detectCorners(bmp)
                            corners = detected
                            isManuallyAdjusted = false
                        }
                    },
                    modifier = Modifier.testTag("auto_detect_corners_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.auto_detect))
                }

                OutlinedButton(
                    onClick = {
                        loadedBitmap?.let { bmp ->
                            corners = Corners.defaultFull(bmp.width.toFloat(), bmp.height.toFloat(), insetRatio = 0.01f)
                            isManuallyAdjusted = true
                        }
                    },
                    modifier = Modifier.testTag("full_page_corners_button")
                ) {
                    Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.full_image))
                }

                OutlinedButton(
                    onClick = {
                        initialDetectedCorners?.let {
                            corners = it
                            isManuallyAdjusted = false
                        }
                    },
                    modifier = Modifier.testTag("reset_corners_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
            }
        }
    }
}

/**
 * Checks if a point (px, py) is inside an arbitrary convex quadrilateral (p1, p2, p3, p4).
 */
private fun isPointInQuad(
    px: Float, py: Float,
    p1: CornerPoint, p2: CornerPoint, p3: CornerPoint, p4: CornerPoint
): Boolean {
    fun crossProduct(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
    }

    val cp1 = crossProduct(p1.x, p1.y, p2.x, p2.y, px, py)
    val cp2 = crossProduct(p2.x, p2.y, p3.x, p3.y, px, py)
    val cp3 = crossProduct(p3.x, p3.y, p4.x, p4.y, px, py)
    val cp4 = crossProduct(p4.x, p4.y, p1.x, p1.y, px, py)

    val allPositive = cp1 >= 0 && cp2 >= 0 && cp3 >= 0 && cp4 >= 0
    val allNegative = cp1 <= 0 && cp2 <= 0 && cp3 <= 0 && cp4 <= 0
    return allPositive || allNegative
}
