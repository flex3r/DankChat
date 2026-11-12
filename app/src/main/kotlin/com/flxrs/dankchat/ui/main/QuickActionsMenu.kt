package com.flxrs.dankchat.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledScrollArea
import com.composeunstyled.UnstyledThumb
import com.composeunstyled.UnstyledVerticalScrollbar
import com.composeunstyled.rememberScrollAreaState
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.appearance.InputAction
import com.flxrs.dankchat.ui.main.input.InputActionsState
import com.flxrs.dankchat.ui.main.input.TourOverlayState
import com.flxrs.dankchat.ui.main.input.overflowMenuItems
import com.flxrs.dankchat.utils.compose.rememberStartAlignedTooltipPositionProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsMenu(
    surfaceColor: Color,
    actionState: InputActionsState,
    visibleActions: ImmutableList<InputAction>,
    isAudioOnly: Boolean,
    tourState: TourOverlayState,
    hasAnyConfiguredActions: Boolean,
    onActionClick: (InputAction) -> Unit,
    onAudioOnly: () -> Unit,
    onHideAllActions: () -> Unit,
    onConfigureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val scrollAreaState = rememberScrollAreaState(scrollState)
    var itemHeightPx by remember { mutableIntStateOf(0) }
    val measureModifier = Modifier.onSizeChanged { if (itemHeightPx == 0) itemHeightPx = it.height }

    val scrollbarAlpha = remember { Animatable(RESTING_SCROLLBAR_ALPHA) }
    LaunchedEffect(Unit) {
        val maxScroll = snapshotFlow { scrollState.maxValue }.first { it != Int.MAX_VALUE }
        if (maxScroll > 0) {
            scrollbarAlpha.snapTo(1f)
            delay(400)
            scrollbarAlpha.animateTo(RESTING_SCROLLBAR_ALPHA, tween(500))
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        color = surfaceColor,
        modifier = modifier,
    ) {
        UnstyledScrollArea(state = scrollAreaState) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .verticalScroll(scrollState),
            ) {
                for (item in actionState.overflowMenuItems(visibleActions)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(item.labelRes)) },
                        onClick = { onActionClick(item.action) },
                        enabled = item.enabled,
                        leadingIcon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                            )
                        },
                    )
                }

                if (actionState.isStreamActive) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isAudioOnly) R.string.menu_exit_audio_only else R.string.menu_audio_only,
                                ),
                            )
                        },
                        onClick = onAudioOnly,
                        enabled = actionState.enabled,
                        leadingIcon = {
                            Icon(
                                imageVector = if (isAudioOnly) Icons.Outlined.Videocam else Icons.Default.Headphones,
                                contentDescription = null,
                            )
                        },
                    )
                }

                HorizontalDivider()

                if (hasAnyConfiguredActions) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.input_action_hide_all)) },
                        onClick = onHideAllActions,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.RemoveCircleOutline,
                                contentDescription = null,
                            )
                        },
                    )
                }

                val configureItem: @Composable () -> Unit = {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.input_action_configure)) },
                        onClick = {
                            when {
                                tourState.configureActionsTooltipState != null -> tourState.onAdvance?.invoke()
                                else -> onConfigureClick()
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                            )
                        },
                        modifier = measureModifier,
                    )
                }
                when {
                    tourState.configureActionsTooltipState != null -> {
                        TooltipBox(
                            positionProvider = rememberStartAlignedTooltipPositionProvider(),
                            tooltip = {
                                EndCaretTourTooltip(
                                    text = stringResource(R.string.tour_configure_actions),
                                    onAction = { tourState.onAdvance?.invoke() },
                                    onSkip = { tourState.onSkip?.invoke() },
                                )
                            },
                            state = tourState.configureActionsTooltipState,
                            // A focusable tooltip takes every touch, so a tap outside of it or a back press ends the tour
                            onDismissRequest = { tourState.onSkip?.invoke() },
                            focusable = true,
                            hasAction = true,
                        ) {
                            configureItem()
                        }
                    }

                    else -> {
                        configureItem()
                    }
                }
            }
            if (scrollState.maxValue > itemHeightPx) {
                UnstyledVerticalScrollbar(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxHeight()
                            .padding(vertical = 6.dp)
                            .padding(end = 6.dp)
                            .width(4.dp),
                ) {
                    UnstyledThumb(
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = scrollbarAlpha.value),
                            RoundedCornerShape(100),
                        ),
                        enabled = false,
                    )
                }
            }
        }
    }
}

/**
 * Tour tooltip positioned to the start of its anchor, with a right-pointing caret on the end side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndCaretTourTooltip(
    text: String,
    onAction: () -> Unit,
    onSkip: () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = containerColor,
            shadowElevation = 2.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 220.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 8.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.tour_skip))
                    }
                    TextButton(onClick = onAction) {
                        Text(stringResource(R.string.tour_next))
                    }
                }
            }
        }
        Canvas(modifier = Modifier.size(width = 12.dp, height = 24.dp)) {
            val path =
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(0f, size.height)
                    close()
                }
            drawPath(path, containerColor)
        }
    }
}
