package com.flxrs.dankchat.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.chat.swipeDownToHide
import com.flxrs.dankchat.utils.compose.bottomInsetsPadding
import kotlinx.coroutines.delay

private const val MIN_VISIBLE_MESSAGE_LINES = 9

@Composable
internal fun BoxScope.NormalStackedLayout(
    currentStream: UserName?,
    isAudioOnly: Boolean,
    isInputScrollable: Boolean,
    streamView: @Composable (StreamViewConfig, Modifier) -> Unit,
    hasWebViewBeenAttached: Boolean,
    streamState: StreamToolbarState,
    scaffoldContent: @Composable (PaddingValues, Dp, Boolean) -> Unit,
    floatingToolbar: @Composable (Modifier, Boolean, Boolean, Boolean) -> Unit,
    fullScreenSheetOverlay: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    emoteMenuLayer: @Composable (Modifier) -> Unit,
    snackbarHostState: SnackbarHostState,
    scaffoldBottomInsets: WindowInsets,
    scaffoldBottomTargetDp: Dp,
    inputHeightDp: Dp,
    isFullscreen: Boolean,
    gestureToolbarHidden: Boolean,
    isKeyboardVisible: Boolean,
    isEmoteMenuOpen: Boolean,
    isSheetOpen: Boolean,
    isInPipMode: Boolean,
    containerWidthPx: Int,
    containerHeightPx: Int,
    fontSize: Int,
    showInput: Boolean,
    inputPopupExpanded: Boolean,
    forceOverflowOpen: Boolean,
    swipeDownThresholdPx: Float,
    suggestionDropdown: @Composable (Modifier) -> Unit,
    onInputSwipeDown: () -> Unit,
    onDismissInputPopup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val showStream = shouldShowStream(
        currentStream = currentStream,
        isAudioOnly = isAudioOnly,
        isInPipMode = isInPipMode,
        isKeyboardVisible = isKeyboardVisible,
        isEmoteMenuOpen = isEmoteMenuOpen,
        containerWidthPx = containerWidthPx,
        containerHeightPx = containerHeightPx,
        scaffoldBottomPadding = scaffoldBottomTargetDp,
        inputHeightDp = inputHeightDp,
        fontSize = fontSize,
        density = density,
    )
    val toolbarVisible = shouldShowToolbar(showStream, isKeyboardVisible, isEmoteMenuOpen, isSheetOpen)

    if (!isInPipMode) {
        Scaffold(
            modifier =
                modifier
                    .fillMaxSize()
                    .bottomInsetsPadding(scaffoldBottomInsets),
            contentWindowInsets = WindowInsets(0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = inputHeightDp),
                    snackbar = { data -> DismissibleSnackbar(data) },
                )
            },
        ) { paddingValues ->
            // The stream fade covers the transition visually, interpolating the padding with the
            // fade alpha would relayout the whole pager every animation frame
            val chatTopPadding = maxOf(with(density) { WindowInsets.statusBars.getTop(density).toDp() }, streamState.heightDp)
            scaffoldContent(paddingValues, chatTopPadding, toolbarVisible)
        }
    }

    // Stream View layer — kept in composition when hidden so the WebView
    // stays attached and audio/video continues playing without re-buffering.
    currentStream?.let { channel ->
        var streamComposed by remember { mutableStateOf(hasWebViewBeenAttached) }
        LaunchedEffect(showStream) {
            if (showStream) {
                delay(100)
                streamComposed = true
            }
        }
        if (streamComposed) {
            streamView(
                StreamViewConfig(
                    channel = channel,
                    isInPipMode = isInPipMode,
                ),
                when {
                    isInPipMode -> {
                        Modifier.fillMaxSize()
                    }

                    showStream -> {
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .graphicsLayer { alpha = streamState.alpha.value }
                            .onSizeChanged { size ->
                                streamState.heightDp = with(density) { size.height.toDp() }
                            }
                    }

                    else -> {
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .graphicsLayer { alpha = 0f }
                    }
                },
            )
        }
        if (!showStream) {
            streamState.heightDp = 0.dp
        }
    }

    // Status bar scrim when stream video is visible (not audio-only, not hidden by fallback)
    if (showStream && !isFullscreen && !isInPipMode) {
        StatusBarScrim(
            colorAlpha = 1f,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = streamState.alpha.value },
        )
    }

    if (!isInPipMode) {
        floatingToolbar(
            Modifier.align(Alignment.TopCenter),
            toolbarVisible,
            true,
            true,
        )
    }

    val statusBarVisible = WindowInsets.statusBars.getTop(density) > 0
    AnimatedStatusBarScrim(
        visible = !isInPipMode && statusBarVisible && (gestureToolbarHidden || isFullscreen),
        modifier = Modifier.align(Alignment.TopCenter),
    )

    if (!isInPipMode) {
        fullScreenSheetOverlay()
    }

    if (!isInPipMode && inputPopupExpanded) {
        InputDismissScrim(
            forceOpen = forceOverflowOpen,
            onDismiss = onDismissInputPopup,
        )
    }

    if (!isInPipMode) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .bottomInsetsPadding(scaffoldBottomInsets)
                    .swipeDownToHide(
                        enabled = showInput && !isSheetOpen && !isInputScrollable && !isEmoteMenuOpen,
                        thresholdPx = swipeDownThresholdPx,
                        onHide = onInputSwipeDown,
                    ),
        ) {
            bottomBar()
        }
    }

    if (!isInPipMode) emoteMenuLayer(Modifier.align(Alignment.BottomCenter))

    if (!isInPipMode && showInput && isKeyboardVisible) {
        suggestionDropdown(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = inputHeightDp + 2.dp),
        )
    }
}

/** Stream stays visible with keyboard/emote open unless there isn't enough space for chat messages. */
private fun shouldShowStream(
    currentStream: UserName?,
    isAudioOnly: Boolean,
    isInPipMode: Boolean,
    isKeyboardVisible: Boolean,
    isEmoteMenuOpen: Boolean,
    containerWidthPx: Int,
    containerHeightPx: Int,
    scaffoldBottomPadding: Dp,
    inputHeightDp: Dp,
    fontSize: Int,
    density: Density,
): Boolean {
    val hasStream = currentStream != null && !isAudioOnly
    if (!hasStream) return false
    if (isInPipMode) return true

    val isInputActive = isKeyboardVisible || isEmoteMenuOpen
    if (!isInputActive || containerHeightPx <= 0) return true

    val containerHeightDp = with(density) { containerHeightPx.toDp() }
    val streamNaturalHeight = with(density) { containerWidthPx.toDp() } * 9 / 16
    val minMessageArea = with(density) { (fontSize * MIN_VISIBLE_MESSAGE_LINES).sp.toDp() }
    val available = containerHeightDp - streamNaturalHeight - scaffoldBottomPadding - inputHeightDp
    return available >= minMessageArea
}

/** Toolbar hides for keyboard/emote only when stream is visible. Always hidden when a sheet is open. */
private fun shouldShowToolbar(
    showStream: Boolean,
    isKeyboardVisible: Boolean,
    isEmoteMenuOpen: Boolean,
    isSheetOpen: Boolean,
): Boolean {
    if (isSheetOpen) return false
    if (!showStream) return true
    return !isKeyboardVisible && !isEmoteMenuOpen
}
