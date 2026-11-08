package com.flxrs.dankchat.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.preferences.appearance.FabAnchor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Layout metrics snapshot used to compute FAB anchor positions in pixel space. */
private data class FabMetrics(
    val containerW: Float,
    val containerH: Float,
    val clusterW: Float,
    val clusterH: Float,
    val horizontalPad: Float,
    val topPad: Float,
    val bottomPad: Float,
    val isRtl: Boolean,
) {
    val sizesReady: Boolean get() = containerW > 0f && clusterW > 0f

    fun anchorToPx(
        anchor: FabAnchor,
        freeX: Float,
        freeY: Float,
        stackOffset: Float = 0f,
    ): Offset {
        val xEnd = if (isRtl) horizontalPad else (containerW - clusterW - horizontalPad).coerceAtLeast(horizontalPad)
        val minY = topPad
        val maxY = (containerH - clusterH - bottomPad).coerceAtLeast(minY)
        return when (anchor) {
            FabAnchor.BottomEnd -> Offset(xEnd, (maxY - stackOffset).coerceAtLeast(minY))

            FabAnchor.TopEnd -> Offset(xEnd, minY)

            FabAnchor.Free -> {
                val minX = horizontalPad
                val maxX = (containerW - clusterW - horizontalPad).coerceAtLeast(minX)
                Offset(
                    (freeX * (maxX - minX) + minX).coerceIn(minX, maxX),
                    (freeY * (maxY - minY) + minY).coerceIn(minY, maxY),
                )
            }
        }
    }

    fun fractionFromPx(pos: Offset): Pair<Float, Float> {
        val minX = horizontalPad
        val maxX = (containerW - clusterW - horizontalPad).coerceAtLeast(minX)
        val minY = topPad
        val maxY = (containerH - clusterH - bottomPad).coerceAtLeast(minY)
        val xFrac = ((pos.x - minX) / (maxX - minX).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val yFrac = ((pos.y - minY) / (maxY - minY).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return xFrac to yFrac
    }

    fun clampToBounds(pos: Offset): Offset {
        val minX = horizontalPad
        val maxX = (containerW - clusterW - horizontalPad).coerceAtLeast(minX)
        val minY = topPad
        val maxY = (containerH - clusterH - bottomPad).coerceAtLeast(minY)
        return Offset(pos.x.coerceIn(minX, maxX), pos.y.coerceIn(minY, maxY))
    }
}

private val SnapThreshold = 40.dp
private const val DRAG_SCALE = 1.06f

/**
 * Positions a FAB cluster at one of three anchors (bottom-end, top-end, or free) with long-press
 * drag-to-reposition and magnetic snapping to the corner anchors.
 */
@Composable
fun FabCluster(
    anchor: FabAnchor,
    offsetXFraction: Float,
    offsetYFraction: Float,
    onPositionChange: (FabAnchor, Float, Float) -> Unit,
    topSystemInset: Dp,
    bottomSystemInset: Dp,
    stackOffsetAtBottomEnd: () -> Dp,
    dragEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (dragModifier: Modifier, isDragging: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val horizontalPadPx = with(density) { 16.dp.toPx() }
    val topPadPx = with(density) { (16.dp + topSystemInset).toPx() }
    val bottomPadPx = with(density) { (24.dp + bottomSystemInset).toPx() }
    val snapThresholdPx = with(density) { SnapThreshold.toPx() }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var clusterSize by remember { mutableStateOf(IntSize.Zero) }
    var hoveredCorner by remember { mutableStateOf<FabAnchor?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var fingerPos by remember { mutableStateOf(Offset.Zero) }
    var initialized by remember { mutableStateOf(false) }
    // Render from offsetAnim between drag release and the external state update.
    var pendingStateSync by remember { mutableStateOf(false) }

    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()

    val currentMetrics = FabMetrics(
        containerW = containerSize.width.toFloat(),
        containerH = containerSize.height.toFloat(),
        clusterW = clusterSize.width.toFloat(),
        clusterH = clusterSize.height.toFloat(),
        horizontalPad = horizontalPadPx,
        topPad = topPadPx,
        bottomPad = bottomPadPx,
        isRtl = isRtl,
    )

    // The stack offset animates with the scroll FAB, reading it in composition would recompose
    // the cluster every animation frame
    val stackOffsetPx = { with(density) { stackOffsetAtBottomEnd().toPx() } }

    LaunchedEffect(containerSize, clusterSize) {
        if (!initialized && currentMetrics.sizesReady) {
            offsetAnim.snapTo(currentMetrics.anchorToPx(anchor, offsetXFraction, offsetYFraction, stackOffsetPx()))
            initialized = true
        }
    }

    // Size and stack changes are read fresh in the offset modifier to avoid chaining a second spring.
    LaunchedEffect(anchor, offsetXFraction, offsetYFraction) {
        if (!initialized || !currentMetrics.sizesReady || isDragging) return@LaunchedEffect
        if (!offsetAnim.isRunning) {
            offsetAnim.animateTo(
                currentMetrics.anchorToPx(anchor, offsetXFraction, offsetYFraction, stackOffsetPx()),
                spring(),
            )
        }
        pendingStateSync = false
    }

    val latestMetrics by rememberUpdatedState(currentMetrics)
    val latestAnchor by rememberUpdatedState(anchor)
    val latestFractionX by rememberUpdatedState(offsetXFraction)
    val latestFractionY by rememberUpdatedState(offsetYFraction)
    val latestStackOffsetPx by rememberUpdatedState(stackOffsetPx)

    val dragBehavior = when {
        !dragEnabled -> Modifier

        else -> Modifier.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    // Seed offsetAnim from the rendered anchor since direct render bypasses it while idle.
                    val currentPos = latestMetrics.anchorToPx(latestAnchor, latestFractionX, latestFractionY, latestStackOffsetPx())
                    scope.launch { offsetAnim.snapTo(currentPos) }
                    fingerPos = currentPos
                    isDragging = true
                },
                onDrag = { change, drag ->
                    change.consume()
                    val m = latestMetrics
                    val newFinger = m.clampToBounds(fingerPos + drag)
                    fingerPos = newFinger
                    val bottomEndPx = m.anchorToPx(FabAnchor.BottomEnd, latestFractionX, latestFractionY, latestStackOffsetPx())
                    val topEndPx = m.anchorToPx(FabAnchor.TopEnd, latestFractionX, latestFractionY)
                    val newCorner = when {
                        (newFinger - bottomEndPx).getDistance() < snapThresholdPx -> FabAnchor.BottomEnd
                        (newFinger - topEndPx).getDistance() < snapThresholdPx -> FabAnchor.TopEnd
                        else -> null
                    }
                    val cornerChanged = newCorner != hoveredCorner
                    hoveredCorner = newCorner
                    when {
                        newCorner == null -> scope.launch { offsetAnim.snapTo(newFinger) }

                        cornerChanged -> {
                            val target = if (newCorner == FabAnchor.BottomEnd) bottomEndPx else topEndPx
                            scope.launch { offsetAnim.animateTo(target, tween(150)) }
                        }
                        // Hovering the same corner — let the in-flight snap animation finish.
                    }
                },
                onDragEnd = {
                    val snapTarget = hoveredCorner
                    val (newAnchor, newFx, newFy) = when (snapTarget) {
                        null -> {
                            val (xFrac, yFrac) = latestMetrics.fractionFromPx(fingerPos)
                            Triple(FabAnchor.Free, xFrac, yFrac)
                        }

                        else -> Triple(snapTarget, 0f, 0f)
                    }
                    onPositionChange(newAnchor, newFx, newFy)
                    // Only gate if state actually changes; otherwise the LaunchedEffect never fires to clear it.
                    pendingStateSync = newAnchor != latestAnchor ||
                        newFx != latestFractionX ||
                        newFy != latestFractionY
                    isDragging = false
                    hoveredCorner = null
                },
                onDragCancel = {
                    isDragging = false
                    hoveredCorner = null
                },
            )
        }
    }

    val scale by animateFloatAsState(if (isDragging) DRAG_SCALE else 1f, tween(150), label = "fabClusterScale")

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it },
    ) {
        Box(
            modifier = Modifier
                // Cap the cluster to the padded container so a tall stack shrinks instead of overflowing the bottom
                .layout { measurable, constraints ->
                    val maxHeight = (constraints.maxHeight - topPadPx - bottomPadPx).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = maxHeight))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }.onSizeChanged { clusterSize = it }
                .offset {
                    val m = FabMetrics(
                        containerW = containerSize.width.toFloat(),
                        containerH = containerSize.height.toFloat(),
                        clusterW = clusterSize.width.toFloat(),
                        clusterH = clusterSize.height.toFloat(),
                        horizontalPad = horizontalPadPx,
                        topPad = topPadPx,
                        bottomPad = bottomPadPx,
                        isRtl = isRtl,
                    )
                    val pos = when {
                        !m.sizesReady -> Offset.Zero
                        isDragging || offsetAnim.isRunning || pendingStateSync -> offsetAnim.value
                        else -> m.anchorToPx(anchor, offsetXFraction, offsetYFraction, stackOffsetAtBottomEnd().toPx())
                    }
                    IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                }.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            content(dragBehavior, isDragging)
        }
    }
}
