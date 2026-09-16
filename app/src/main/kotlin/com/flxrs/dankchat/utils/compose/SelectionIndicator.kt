package com.flxrs.dankchat.utils.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * Tracks per-tab position and width so [PagerTabIndicator] can interpolate smoothly during swipes.
 * Use [reportPosition] on each tab's modifier chain.
 */
@Stable
class PagerTabIndicatorState internal constructor(
    pageCount: Int,
) {
    internal val offsets = mutableStateListOf<Int>().apply { repeat(pageCount) { add(0) } }
    internal val widths = mutableStateListOf<Int>().apply { repeat(pageCount) { add(0) } }

    internal fun update(
        index: Int,
        offset: Int,
        width: Int,
    ) {
        if (index !in offsets.indices) return
        if (offsets[index] != offset) offsets[index] = offset
        if (widths[index] != width) widths[index] = width
    }

    /** Returns true once all tabs have reported a non-zero width. */
    internal val ready: Boolean
        get() = widths.isNotEmpty() && widths.all { it > 0 }
}

@Composable
fun rememberPagerTabIndicatorState(pageCount: Int): PagerTabIndicatorState = remember(pageCount) { PagerTabIndicatorState(pageCount) }

/** Captures a tab's offset and width into [state] for the given [index]. */
fun Modifier.reportPosition(
    state: PagerTabIndicatorState,
    index: Int,
): Modifier = onGloballyPositioned { coords: LayoutCoordinates ->
    state.update(index, coords.positionInParent().x.toInt(), coords.size.width)
}

/**
 * Animated underline that tracks the current pager page. Interpolates position and width during
 * swipes between adjacent tabs. Place inside the same scrollable container as the tab row so it
 * scrolls in lockstep with the tabs.
 */
@Composable
fun PagerTabIndicator(
    pagerState: PagerState,
    state: PagerTabIndicatorState,
    color: Color,
    modifier: Modifier = Modifier,
    pageIndex: (Int) -> Int = { it },
    height: Dp = 3.dp,
    bottomInset: Dp = 8.dp,
) {
    if (!state.ready || pagerState.pageCount == 0) return
    val radius = height / 2
    Box(
        modifier = modifier
            // defer reads of the frequently changing pager offset to the layout phase to avoid recomposing on every swipe frame
            .layout { measurable, _ ->
                val currentPage = pagerState.currentPage
                val offsetFraction = pagerState.currentPageOffsetFraction
                val adjacentPage = currentPage + if (offsetFraction < 0f) -1 else 1
                val currentIndex = pageIndex(currentPage).coerceIn(0, state.offsets.lastIndex)
                val adjacentIndex = pageIndex(adjacentPage).coerceIn(0, state.offsets.lastIndex)
                val fraction = offsetFraction.absoluteValue
                val offsetPx = (state.offsets[currentIndex] * (1f - fraction) + state.offsets[adjacentIndex] * fraction).toInt()
                val widthPx = (state.widths[currentIndex] * (1f - fraction) + state.widths[adjacentIndex] * fraction).toInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(widthPx, height.roundToPx()))
                layout(placeable.width, placeable.height) {
                    placeable.place(offsetPx, -bottomInset.roundToPx())
                }
            }.background(color, RoundedCornerShape(radius)),
    )
}

/**
 * Draws a vertical accent bar near the start edge when [selected] is true. Apply before any
 * horizontal padding so the bar renders within the item's own bounds without shifting content.
 */
fun Modifier.selectedIndicatorBar(
    selected: Boolean,
    color: Color,
    width: Dp = 4.dp,
    verticalInset: Dp = 8.dp,
    startInset: Dp = 6.dp,
): Modifier = drawBehind {
    if (!selected) return@drawBehind
    val barWidthPx = width.toPx()
    val verticalInsetPx = verticalInset.toPx()
    val startInsetPx = startInset.toPx()
    val radius = barWidthPx / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(startInsetPx, verticalInsetPx),
        size = Size(barWidthPx, (size.height - verticalInsetPx * 2).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(radius, radius),
    )
}
