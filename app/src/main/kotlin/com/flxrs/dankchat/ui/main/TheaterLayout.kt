package com.flxrs.dankchat.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.chat.ChatComposable
import com.flxrs.dankchat.ui.chat.ChatScrollState
import com.flxrs.dankchat.ui.chat.emote.LocalChatPageVisible
import com.flxrs.dankchat.ui.chat.swipeDownToHide
import com.flxrs.dankchat.utils.compose.bottomInsetsPadding

internal val THEATER_CHAT_WIDTH = 320.dp
internal val MIN_DOCKED_THEATER_CHAT_WIDTH = 160.dp
private const val THEATER_CHAT_ALPHA = 0.92f

// Theater mode: the stream fills the whole screen and the chat is a fixed-width translucent
// overlay on the end side that never shrinks the video
@Composable
internal fun TheaterLayout(
    currentStream: UserName,
    chatScrollState: ChatScrollState,
    isChatVisible: Boolean,
    isChatDocked: Boolean,
    dockedChatWidth: Dp,
    canDockChat: Boolean,
    onToggleChatMode: () -> Unit,
    onChatPanelWidthChange: (Float) -> Unit,
    showInput: Boolean,
    isKeyboardVisible: Boolean,
    isSheetOpen: Boolean,
    isInputMultiline: Boolean,
    isEmoteMenuOpen: Boolean,
    inputHeightDp: Dp,
    helperTextHeightDp: Dp,
    swipeDownThresholdPx: Float,
    scaffoldBottomInsets: WindowInsets,
    chatOffsetX: () -> Float,
    onChatDrag: (Float) -> Unit,
    onChatDragEnd: () -> Unit,
    onHideInput: () -> Unit,
    onOpenReplies: (String, UserName) -> Unit,
    onRecover: () -> Unit,
    streamView: @Composable (StreamViewConfig, Modifier) -> Unit,
    bottomBar: @Composable () -> Unit,
    emoteMenuLayer: @Composable (Modifier) -> Unit,
    fullScreenSheetOverlay: @Composable () -> Unit,
    suggestionDropdown: @Composable (Modifier) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // The chat drag gestures in the stream touch listener work in raw screen coordinates
        // and assume the panel on the right, so the theater surface is pinned to LTR while
        // fullscreen sheets on top keep the real layout direction
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(modifier = Modifier.fillMaxSize()) {
                val isDocked = isChatDocked && canDockChat

                // Single driver for the mode switch: panel width, panel opacity and the stream shift
                // all derive from it, so everything moves together
                val dockProgress by animateFloatAsState(
                    targetValue =
                        when {
                            isDocked -> 1f
                            else -> 0f
                        },
                    animationSpec = tween(durationMillis = 300),
                    label = "theaterDockProgress",
                )
                val density = LocalDensity.current
                val panelWidth = lerp(THEATER_CHAT_WIDTH, dockedChatWidth, dockProgress)
                val panelWidthPx = with(density) { panelWidth.toPx() }
                val targetPanelWidth =
                    when {
                        isDocked -> dockedChatWidth
                        else -> THEATER_CHAT_WIDTH
                    }
                val targetPanelWidthPx = with(density) { targetPanelWidth.toPx() }
                SideEffect { onChatPanelWidthChange(targetPanelWidthPx) }

                // The stream keeps its full size while the chat moves, because the web player reflows resizes
                // asynchronously, which glitches during animations. Shifting it left by half the visible chat puts
                // the full-height video exactly in the remaining area.
                // The player controls span the whole WebView, so once docking has settled with the chat shown,
                // the WebView is inset by half the docked chat on both sides, which lands it exactly left of the chat.
                // The inset is dropped again only once the chat is fully hidden or undocking has finished.
                val isChatFullyShown by remember(chatOffsetX) { derivedStateOf { chatOffsetX() == 0f } }
                val isChatFullyHidden by remember(chatOffsetX, targetPanelWidthPx) { derivedStateOf { chatOffsetX() >= targetPanelWidthPx } }
                val canFitStream = isDocked && isChatVisible && dockProgress == 1f && isChatFullyShown
                val mustUnfitStream = dockProgress == 0f || isChatFullyHidden
                var isStreamFitted by remember { mutableStateOf(false) }
                SideEffect(canFitStream, mustUnfitStream) {
                    when {
                        canFitStream -> isStreamFitted = true
                        mustUnfitStream -> isStreamFitted = false
                    }
                }
                val streamModifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val visibleChat = (panelWidthPx - chatOffsetX()).coerceAtLeast(0f)
                            translationX = -dockProgress * visibleChat / 2f
                        }
                streamView(
                    StreamViewConfig(
                        channel = currentStream,
                        fillPane = true,
                        isTheaterMode = true,
                        isTheaterChatVisible = isChatVisible,
                        canDockTheaterChat = canDockChat,
                        overlayEndPadding =
                            when {
                                // Keeps the buttons left of the chat in both modes: the stream layer
                                // shift already moves them by half the panel when docked
                                isChatVisible -> panelWidth * (1f - dockProgress / 2f)

                                else -> 0.dp
                            },
                        webViewHorizontalInset =
                            when {
                                isStreamFitted -> dockedChatWidth / 2
                                else -> 0.dp
                            },
                    ),
                    streamModifier,
                )

                // Always composed but translated off-screen when hidden, so drags can reveal it
                // continuously. Visibility gating keeps the hidden panel from collecting or animating.
                CompositionLocalProvider(LocalChatPageVisible provides isChatVisible) {
                    val panelAlpha = lerp(THEATER_CHAT_ALPHA, 1f, dockProgress)
                    val panelColor = MaterialTheme.colorScheme.surface.copy(alpha = panelAlpha)
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(panelWidth)
                                .graphicsLayer { translationX = chatOffsetX() }
                                .background(panelColor)
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = { onChatDragEnd() },
                                        onDragCancel = { onChatDragEnd() },
                                    ) { _, dragAmount -> onChatDrag(dragAmount) }
                                },
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .bottomInsetsPadding(scaffoldBottomInsets),
                        ) {
                            ChatComposable(
                                channel = currentStream,
                                onReplyClick = onOpenReplies,
                                scrollState = chatScrollState,
                                isPageVisible = isChatVisible,
                                showInput = showInput,
                                showPinnedMessage = false,
                                onRecover = onRecover,
                                showTheaterChatModeFab = canDockChat,
                                isTheaterChatDocked = isDocked,
                                onToggleTheaterChatMode = onToggleChatMode,
                                containerColor = Color.Transparent,
                                contentPadding =
                                    PaddingValues(
                                        top = 8.dp,
                                        bottom =
                                            when {
                                                showInput -> inputHeightDp
                                                helperTextHeightDp > 0.dp -> helperTextHeightDp
                                                else -> 8.dp
                                            },
                                    ),
                            )

                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .swipeDownToHide(
                                            enabled = showInput && !isSheetOpen && !isInputMultiline && !isKeyboardVisible && !isEmoteMenuOpen,
                                            thresholdPx = swipeDownThresholdPx,
                                            onHide = onHideInput,
                                        ),
                            ) {
                                bottomBar()
                            }

                            if (showInput && isKeyboardVisible) {
                                suggestionDropdown(
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(bottom = inputHeightDp + 2.dp),
                                )
                            }

                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = inputHeightDp),
                                snackbar = { data -> DismissibleSnackbar(data) },
                            )
                        }

                        emoteMenuLayer(Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }

        fullScreenSheetOverlay()
    }
}
