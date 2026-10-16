package com.flxrs.dankchat.ui.main.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.flxrs.dankchat.R

@OptIn(ExperimentalMaterial3Api::class)
@Immutable
data class TourOverlayState(
    val inputActionsTooltipState: TooltipState? = null,
    val overflowMenuTooltipState: TooltipState? = null,
    val configureActionsTooltipState: TooltipState? = null,
    val swipeGestureTooltipState: TooltipState? = null,
    val forceOverflowOpen: Boolean = false,
    val isTourActive: Boolean = false,
    val onAdvance: (() -> Unit)? = null,
    val onSkip: (() -> Unit)? = null,
)

@Suppress("ContentSlotReused")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionalTourTooltip(
    tooltipState: TooltipState?,
    text: String,
    onAdvance: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    focusable: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (tooltipState != null) {
        TooltipBox(
            positionProvider = rememberTourTooltipPositionProvider(),
            tooltip = {
                TourTooltip(
                    text = text,
                    onAction = { onAdvance?.invoke() },
                    onSkip = { onSkip?.invoke() },
                )
            },
            state = tooltipState,
            onDismissRequest = {},
            focusable = focusable,
            hasAction = true,
        ) {
            content()
        }
    } else {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TooltipScope.TourTooltip(
    text: String,
    onAction: () -> Unit,
    onSkip: () -> Unit,
    isLast: Boolean = false,
    showCaret: Boolean = true,
) {
    val tourColors =
        TooltipDefaults.richTooltipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionContentColor = MaterialTheme.colorScheme.secondary,
        )
    RichTooltip(
        colors = tourColors,
        caretShape = if (showCaret) TooltipDefaults.caretShape(caretSize = DpSize(24.dp, 12.dp)) else null,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.tour_skip))
                }
                TextButton(onClick = onAction) {
                    Text(stringResource(if (isLast) R.string.tour_got_it else R.string.tour_next))
                }
            }
        },
    ) {
        Text(text)
    }
}

/**
 * Places tooltips above their anchor where material3's caret expects them.
 * The default provider aligns overflowing tooltips to the window edge, while the caret assumes the anchor edge.
 */
@Composable
internal fun rememberTourTooltipPositionProvider(spacingToAnchor: Dp = 4.dp): PopupPositionProvider {
    val spacing = with(LocalDensity.current) { spacingToAnchor.roundToPx() }
    val windowWidth = LocalWindowInfo.current.containerSize.width
    return remember(spacing, windowWidth) { TourTooltipPositionProvider(spacing, windowWidth) }
}

private class TourTooltipPositionProvider(
    private val spacing: Int,
    private val windowWidth: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val anchorCenter = (anchorBounds.left + anchorBounds.right) / 2
        val x =
            when {
                width >= windowWidth -> 0
                anchorCenter - width / 2 < 0 -> minOf(anchorBounds.left, windowWidth - width)
                anchorCenter + width / 2 > windowWidth -> maxOf(anchorBounds.right - width, 0)
                else -> anchorCenter - width / 2
            }
        val above = anchorBounds.top - popupContentSize.height - spacing
        val y =
            when {
                above < 0 -> anchorBounds.bottom + spacing
                else -> above
            }
        return IntOffset(x, y)
    }
}
