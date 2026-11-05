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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledScrollArea
import com.composeunstyled.UnstyledThumb
import com.composeunstyled.UnstyledVerticalScrollbar
import com.composeunstyled.rememberScrollAreaState
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.appearance.InputAction
import com.flxrs.dankchat.ui.main.input.TourOverlayState
import com.flxrs.dankchat.utils.compose.rememberStartAlignedTooltipPositionProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsMenu(
    surfaceColor: Color,
    visibleActions: ImmutableList<InputAction>,
    enabled: Boolean,
    hasLastMessage: Boolean,
    isStreamActive: Boolean,
    isAudioOnly: Boolean,
    hasStreamData: Boolean,
    isFullscreen: Boolean,
    isTTSEnabled: Boolean,
    isTheaterMode: Boolean,
    isModerator: Boolean,
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
                for (action in InputAction.entries) {
                    // Theater mode is already fullscreen, so toggling chat fullscreen makes no sense there
                    val isHiddenInTheater = isTheaterMode && action == InputAction.Fullscreen
                    // Theater renders below next to the audio only entry, grouping the stream modes
                    if (action == InputAction.Theater || action in visibleActions || isHiddenInTheater) {
                        continue
                    }
                    val overflowItem =
                        getOverflowItem(
                            action = action,
                            isStreamActive = isStreamActive,
                            hasStreamData = hasStreamData,
                            isFullscreen = isFullscreen,
                            isTTSEnabled = isTTSEnabled,
                            isTheaterMode = isTheaterMode,
                            isModerator = isModerator,
                        )
                    if (overflowItem != null) {
                        val actionEnabled = isActionEnabled(action, enabled, hasLastMessage)
                        DropdownMenuItem(
                            text = { Text(stringResource(overflowItem.labelRes)) },
                            onClick = { onActionClick(action) },
                            enabled = actionEnabled,
                            leadingIcon = {
                                Icon(
                                    imageVector = overflowItem.icon,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }

                val theaterItem =
                    when {
                        InputAction.Theater in visibleActions -> null

                        else ->
                            getOverflowItem(
                                action = InputAction.Theater,
                                isStreamActive = isStreamActive,
                                hasStreamData = hasStreamData,
                                isFullscreen = isFullscreen,
                                isTTSEnabled = isTTSEnabled,
                                isTheaterMode = isTheaterMode,
                                isModerator = isModerator,
                            )
                    }
                if (theaterItem != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(theaterItem.labelRes)) },
                        onClick = { onActionClick(InputAction.Theater) },
                        enabled = isActionEnabled(InputAction.Theater, enabled, hasLastMessage),
                        leadingIcon = {
                            Icon(
                                imageVector = theaterItem.icon,
                                contentDescription = null,
                            )
                        },
                    )
                }

                if (isStreamActive) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isAudioOnly) R.string.menu_exit_audio_only else R.string.menu_audio_only,
                                ),
                            )
                        },
                        onClick = onAudioOnly,
                        enabled = enabled,
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
                            onDismissRequest = {},
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

@Immutable
private data class OverflowItem(
    val labelRes: Int,
    val icon: ImageVector,
)

private fun getOverflowItem(
    action: InputAction,
    isStreamActive: Boolean,
    hasStreamData: Boolean,
    isFullscreen: Boolean,
    isTTSEnabled: Boolean,
    isTheaterMode: Boolean,
    isModerator: Boolean,
): OverflowItem? = when (action) {
    InputAction.Search -> {
        OverflowItem(
            labelRes = R.string.input_action_search,
            icon = Icons.Default.Search,
        )
    }

    InputAction.LastMessage -> {
        OverflowItem(
            labelRes = R.string.input_action_last_message,
            icon = Icons.Default.History,
        )
    }

    InputAction.Stream -> {
        when {
            hasStreamData || isStreamActive -> {
                OverflowItem(
                    labelRes = if (isStreamActive) R.string.menu_hide_stream else R.string.menu_show_stream,
                    icon = if (isStreamActive) Icons.Outlined.VideocamOff else Icons.Outlined.Videocam,
                )
            }

            else -> {
                null
            }
        }
    }

    InputAction.ModActions -> {
        when {
            isModerator -> {
                OverflowItem(
                    labelRes = R.string.menu_mod_actions,
                    icon = Icons.Outlined.Shield,
                )
            }

            else -> {
                null
            }
        }
    }

    InputAction.Fullscreen -> {
        OverflowItem(
            labelRes = if (isFullscreen) R.string.menu_exit_fullscreen else R.string.menu_fullscreen,
            icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
        )
    }

    InputAction.Theater -> {
        when {
            isStreamActive -> {
                OverflowItem(
                    labelRes =
                        when {
                            isTheaterMode -> R.string.menu_exit_theater_mode
                            else -> R.string.menu_theater_mode
                        },
                    icon = Icons.Default.Theaters,
                )
            }

            else -> {
                null
            }
        }
    }

    InputAction.HideInput -> {
        OverflowItem(
            labelRes = R.string.menu_hide_input,
            icon = Icons.Default.VisibilityOff,
        )
    }

    InputAction.TTS -> {
        OverflowItem(
            labelRes = if (isTTSEnabled) R.string.menu_disable_tts else R.string.menu_enable_tts,
            icon = if (isTTSEnabled) Icons.Default.VoiceOverOff else Icons.Default.RecordVoiceOver,
        )
    }

    InputAction.Debug -> {
        OverflowItem(
            labelRes = R.string.input_action_debug,
            icon = Icons.Default.BugReport,
        )
    }
}

private fun isActionEnabled(
    action: InputAction,
    inputEnabled: Boolean,
    hasLastMessage: Boolean,
): Boolean = when (action) {
    InputAction.Search, InputAction.Fullscreen, InputAction.HideInput, InputAction.TTS, InputAction.Debug -> true
    InputAction.LastMessage -> inputEnabled && hasLastMessage
    InputAction.Stream, InputAction.ModActions, InputAction.Theater -> inputEnabled
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
