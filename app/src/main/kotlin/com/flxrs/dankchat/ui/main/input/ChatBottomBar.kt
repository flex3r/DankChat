package com.flxrs.dankchat.ui.main.input

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.preferences.appearance.InputAction
import com.flxrs.dankchat.utils.compose.rememberRoundedCornerHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatBottomBar(
    showInput: Boolean,
    textFieldState: TextFieldState,
    uiState: ChatInputUiState,
    characterCounter: StateFlow<CharacterCounterState>,
    callbacks: ChatInputCallbacks,
    isUploading: Boolean,
    isLoading: Boolean,
    isFullscreen: Boolean,
    isTTSEnabled: Boolean,
    isModerator: Boolean,
    isStreamActive: Boolean,
    isAudioOnly: Boolean,
    hasStreamData: Boolean,
    isSheetOpen: Boolean,
    inputActions: ImmutableList<InputAction>,
    onInputHeightChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    debugMode: Boolean = false,
    overflowExpanded: Boolean = false,
    onOverflowExpandedChange: (Boolean) -> Unit = {},
    recentMessagesExpanded: Boolean = false,
    onRecentMessagesExpandedChange: (Boolean) -> Unit = {},
    onHelperTextHeightChange: (Int) -> Unit = {},
    isInSplitLayout: Boolean = false,
    isTheaterMode: Boolean = false,
    showTheaterDockToggle: Boolean = false,
    isTheaterChatDocked: Boolean = false,
    onToggleTheaterChatMode: () -> Unit = {},
    instantHide: Boolean = false,
    tourState: TourOverlayState = TourOverlayState(),
    isRepeatedSendEnabled: Boolean = false,
    overflowMenuMaxHeightDp: Dp = Dp.Unspecified,
) {
    val inputVisibleState = remember { MutableTransitionState(showInput) }
    inputVisibleState.targetState = showInput
    val inputFullyHidden = !inputVisibleState.targetState && inputVisibleState.isIdle

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visibleState = inputVisibleState,
            enter = EnterTransition.None,
            exit =
                when {
                    instantHide -> ExitTransition.None
                    else -> slideOutVertically(targetOffsetY = { it })
                },
        ) {
            ChatInputLayout(
                textFieldState = textFieldState,
                uiState = uiState,
                characterCounter = characterCounter,
                callbacks = callbacks,
                isSheetOpen = isSheetOpen,
                isUploading = isUploading,
                isLoading = isLoading,
                isFullscreen = isFullscreen,
                isTTSEnabled = isTTSEnabled,
                isModerator = isModerator,
                isStreamActive = isStreamActive,
                isAudioOnly = isAudioOnly,
                hasStreamData = hasStreamData,
                inputActions = inputActions,
                isTheaterMode = isTheaterMode,
                showTheaterDockToggle = showTheaterDockToggle,
                isTheaterChatDocked = isTheaterChatDocked,
                onToggleTheaterChatMode = onToggleTheaterChatMode,
                debugMode = debugMode,
                overflowExpanded = overflowExpanded,
                onOverflowExpandedChange = onOverflowExpandedChange,
                recentMessagesExpanded = recentMessagesExpanded,
                onRecentMessagesExpandedChange = onRecentMessagesExpandedChange,
                tourState = tourState,
                isRepeatedSendEnabled = isRepeatedSendEnabled,
                overflowMenuMaxHeightDp = overflowMenuMaxHeightDp,
                modifier =
                    Modifier.onSizeChanged { size ->
                        onInputHeightChange(size.height)
                    },
            )
        }

        // Sticky helper text + nav bar spacer — wait for exit animation to finish
        if (inputFullyHidden && !isSheetOpen) {
            val helperTextState = uiState.helperText
            if (!helperTextState.isEmpty) {
                val horizontalPadding =
                    when {
                        isFullscreen && isInSplitLayout -> {
                            val rcPadding = rememberRoundedCornerHorizontalPadding(fallback = 16.dp)
                            val direction = LocalLayoutDirection.current
                            PaddingValues(start = 16.dp, end = rcPadding.calculateEndPadding(direction))
                        }

                        isFullscreen -> {
                            rememberRoundedCornerHorizontalPadding(fallback = 16.dp)
                        }

                        else -> {
                            PaddingValues(horizontal = 16.dp)
                        }
                    }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { onHelperTextHeightChange(it.height) },
                ) {
                    ExpandableHelperText(
                        helperText = helperTextState,
                        modifier =
                            Modifier
                                .navigationBarsPadding()
                                .padding(horizontalPadding)
                                .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}
