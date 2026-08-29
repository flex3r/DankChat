package com.flxrs.dankchat.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.chat.swipeDownToHide
import com.flxrs.dankchat.ui.main.stream.StreamViewModel
import com.flxrs.dankchat.utils.compose.bottomInsetsPadding
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.WideSplitLayout(
    currentStream: UserName?,
    isAudioOnly: Boolean,
    streamView: @Composable (StreamViewConfig, Modifier) -> Unit,
    scaffoldContent: @Composable (PaddingValues, Dp, Boolean) -> Unit,
    floatingToolbar: @Composable (Modifier, Boolean, Boolean, Boolean) -> Unit,
    fullScreenSheetOverlay: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    emoteMenuLayer: @Composable (Modifier) -> Unit,
    snackbarHostState: SnackbarHostState,
    scaffoldBottomInsets: WindowInsets,
    inputHeightDp: Dp,
    isFullscreen: Boolean,
    gestureToolbarHidden: Boolean,
    isKeyboardVisible: Boolean,
    isEmoteMenuOpen: Boolean,
    isSheetOpen: Boolean,
    isToolbarMenuOpen: Boolean,
    showInput: Boolean,
    isInputScrollable: Boolean,
    inputPopupExpanded: Boolean,
    forceOverflowOpen: Boolean,
    swipeDownThresholdPx: Float,
    suggestionDropdown: @Composable (Modifier) -> Unit,
    onInputSwipeDown: () -> Unit,
    onDismissInputPopup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val streamViewModel: StreamViewModel = koinViewModel()
    var splitFraction by remember { mutableFloatStateOf(streamViewModel.splitFraction) }
    var containerWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { containerWidthPx = it.width },
    ) {
        SplitPaneLayout(
            splitFraction = { splitFraction },
            isAudioOnly = isAudioOnly,
            modifier = Modifier.fillMaxSize(),
            // Left pane: Stream (hidden but composed in audio-only mode to keep audio playing)
            left = {
                streamView(
                    StreamViewConfig(
                        channel = currentStream ?: return@SplitPaneLayout,
                        fillPane = true,
                    ),
                    Modifier.fillMaxSize(),
                )
            },
            // Right pane: Chat + all overlays
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val statusBarTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
                val toolbarVisible = !isKeyboardVisible && !isEmoteMenuOpen && !isSheetOpen

                Scaffold(
                    modifier =
                        Modifier
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
                    scaffoldContent(paddingValues, statusBarTop, toolbarVisible)
                }

                val showTabsInSplit by remember(density) {
                    derivedStateOf {
                        val chatPaneWidthDp = with(density) { (containerWidthPx * (1f - splitFraction)).toInt().toDp() }
                        chatPaneWidthDp > 250.dp
                    }
                }

                floatingToolbar(
                    Modifier.align(Alignment.TopCenter),
                    toolbarVisible,
                    false,
                    showTabsInSplit,
                )

                val statusBarVisible = WindowInsets.statusBars.getTop(density) > 0
                AnimatedStatusBarScrim(
                    visible = statusBarVisible && (gestureToolbarHidden || isFullscreen),
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                fullScreenSheetOverlay()

                if (inputPopupExpanded) {
                    InputDismissScrim(
                        forceOpen = forceOverflowOpen,
                        onDismiss = onDismissInputPopup,
                    )
                }

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

                emoteMenuLayer(Modifier.align(Alignment.BottomCenter))

                if (showInput && isKeyboardVisible) {
                    suggestionDropdown(
                        Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(bottom = inputHeightDp + 2.dp),
                    )
                }
            }
        }

        if (!isAudioOnly && !isToolbarMenuOpen) {
            DraggableHandle(
                onDrag = { deltaPx ->
                    if (containerWidthPx > 0) {
                        splitFraction = (splitFraction + deltaPx / containerWidthPx).coerceIn(0.2f, 0.8f)
                    }
                },
                onDragEnd = { streamViewModel.setSplitFraction(splitFraction) },
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer { translationX = containerWidthPx * splitFraction - 12.dp.toPx() },
            )
        }
    }
}

// The fraction is read in the measure pass, so drags relayout the panes without recomposing them
@Composable
private fun SplitPaneLayout(
    splitFraction: () -> Float,
    isAudioOnly: Boolean,
    left: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    right: @Composable () -> Unit,
) {
    Layout(
        content = {
            left()
            right()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val leftWidth = when {
            isAudioOnly -> 0
            else -> (width * splitFraction()).roundToInt()
        }
        val rightWidth = width - leftWidth
        val leftPlaceable = measurables[0].measure(Constraints.fixed(leftWidth, height))
        val rightPlaceable = measurables[1].measure(Constraints.fixed(rightWidth, height))
        layout(width, height) {
            leftPlaceable.place(0, 0)
            rightPlaceable.place(leftWidth, 0)
        }
    }
}
