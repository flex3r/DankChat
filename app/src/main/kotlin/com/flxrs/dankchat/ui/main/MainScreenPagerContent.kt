package com.flxrs.dankchat.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.preferences.components.DankBackground
import com.flxrs.dankchat.ui.chat.ChatComposable
import com.flxrs.dankchat.ui.chat.FabMenuCallbacks
import com.flxrs.dankchat.ui.chat.emote.LocalChatPageVisible
import com.flxrs.dankchat.ui.main.channel.ChannelPagerUiState
import com.flxrs.dankchat.ui.main.channel.ChannelTabUiState
import com.flxrs.dankchat.ui.tour.TourStep
import kotlinx.collections.immutable.ImmutableMap

/**
 * Blocks stray horizontal drag input from reaching the pager while a vertical drag inside a
 * page is in progress. During a diagonal chat scroll the pager's drag detector can belatedly
 * win the gesture and deliver the accumulated horizontal component as one large delta,
 * visibly jerking the pager sideways. A vertical drag is recognized by its pure-vertical
 * user-input deltas bubbling up; the block lifts once the gesture ends in a fling.
 * Intentional page swipes dispatch pure-horizontal deltas from the start and pass through.
 */
private class PagerCrossAxisGestureGuard : NestedScrollConnection {
    private var verticalDragActive = false

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput) {
            return Offset.Zero
        }
        return when {
            available.y != 0f && available.x == 0f -> {
                verticalDragActive = true
                Offset.Zero
            }

            verticalDragActive && available.x != 0f -> Offset(available.x, 0f)

            else -> Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        verticalDragActive = false
        return Velocity.Zero
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        verticalDragActive = false
        return Velocity.Zero
    }
}

// Visible pages render fully, offscreen neighbors upgrade from stubs once the pager settles
// and stay active while retained, so gestures never tear down an already composed page
@Composable
private fun rememberIsPageCollectionActive(
    page: Int,
    pagerState: PagerState,
    visibleNeighbor: Int?,
): Boolean {
    val wasActive = remember { mutableStateOf(false) }
    val isNearCurrentPage = (page - pagerState.currentPage) in -1..1
    val isActive = page == pagerState.currentPage ||
        page == visibleNeighbor ||
        (isNearCurrentPage && (wasActive.value || !pagerState.isScrollInProgress))
    wasActive.value = isActive
    return isActive
}

@Stable
internal class ChatPagerCallbacks(
    val onOpenReplies: (String, UserName) -> Unit,
    val onRecover: () -> Unit,
    val onScrollToBottom: () -> Unit,
    val onTourAdvance: () -> Unit,
    val onTourSkip: () -> Unit,
    val scrollConnection: NestedScrollConnection? = null,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreenPagerContent(
    paddingValues: PaddingValues,
    chatTopPadding: Dp,
    tabState: ChannelTabUiState,
    composePagerState: PagerState,
    pagerState: ChannelPagerUiState,
    isLoggedIn: Boolean,
    showInput: Boolean,
    isFullscreen: Boolean,
    swipeNavigation: Boolean,
    isSheetOpen: Boolean,
    inputHeightDp: Dp,
    helperTextHeightDp: Dp,
    navBarHeightDp: Dp,
    effectiveRoundedCorner: Dp,
    scrollTargets: ImmutableMap<UserName, String>,
    onClearScrollTarget: (UserName) -> Unit,
    callbacks: ChatPagerCallbacks,
    fabMenuCallbacks: FabMenuCallbacks?,
    showPinnedMessage: Boolean,
    isToolbarMenuOpen: Boolean,
    currentTourStep: TourStep?,
    recoveryFabTooltipState: TooltipState?,
    onAddChannel: () -> Unit,
    onLogin: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val showFullScreenLoading = tabState.loading && tabState.tabs.isEmpty()
        DankBackground(visible = showFullScreenLoading)
        if (showFullScreenLoading) {
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(paddingValues),
            )
            return@Box
        }
        if (tabState.tabs.isEmpty() && !tabState.loading) {
            EmptyStateContent(
                isLoggedIn = isLoggedIn,
                onAddChannel = onAddChannel,
                onLogin = onLogin,
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding()),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val crossAxisGestureGuard = remember { PagerCrossAxisGestureGuard() }
                    val visibleNeighbor by remember(composePagerState) {
                        derivedStateOf {
                            val fraction = composePagerState.currentPageOffsetFraction
                            when {
                                fraction > 0.01f -> composePagerState.currentPage + 1
                                fraction < -0.01f -> composePagerState.currentPage - 1
                                else -> null
                            }
                        }
                    }
                    HorizontalPager(
                        state = composePagerState,
                        modifier = Modifier.fillMaxSize().nestedScroll(crossAxisGestureGuard).edgeGestureGuard(),
                        userScrollEnabled = swipeNavigation,
                        beyondViewportPageCount = if (pagerState.channels.size == 2) 0 else 1,
                        key = { page ->
                            val channel = pagerState.channels[circularPageToChannelIndex(page, pagerState.channels.size)]
                            "$page:${channel.value}"
                        },
                    ) { page ->
                        if (pagerState.channels.isNotEmpty()) {
                            val channelIndex = circularPageToChannelIndex(page, pagerState.channels.size)
                            val channel = pagerState.channels[channelIndex]
                            val isPageVisible = page == composePagerState.currentPage || page == visibleNeighbor
                            CompositionLocalProvider(LocalChatPageVisible provides isPageVisible) {
                                ChatComposable(
                                    channel = channel,
                                    isCollectionActive = rememberIsPageCollectionActive(page, composePagerState, visibleNeighbor),
                                    isPageVisible = isPageVisible,
                                    onReplyClick = { replyMessageId, replyName ->
                                        callbacks.onOpenReplies(replyMessageId, replyName)
                                    },
                                    showInput = showInput,
                                    isFullscreen = isFullscreen,
                                    showFabs = !isSheetOpen,
                                    onRecover = callbacks.onRecover,
                                    fabMenuCallbacks = fabMenuCallbacks,
                                    showPinnedMessage = showPinnedMessage,
                                    isToolbarMenuOpen = isToolbarMenuOpen,
                                    contentPadding =
                                        PaddingValues(
                                            top = chatTopPadding + if (isFullscreen) 0.dp else 56.dp,
                                            bottom =
                                                paddingValues.calculateBottomPadding() +
                                                    when {
                                                        showInput -> {
                                                            inputHeightDp
                                                        }

                                                        !isFullscreen -> {
                                                            when {
                                                                helperTextHeightDp > 0.dp -> helperTextHeightDp
                                                                else -> max(navBarHeightDp, effectiveRoundedCorner)
                                                            }
                                                        }

                                                        else -> {
                                                            when {
                                                                helperTextHeightDp > 0.dp -> helperTextHeightDp
                                                                else -> effectiveRoundedCorner
                                                            }
                                                        }
                                                    },
                                        ),
                                    scrollModifier = if (callbacks.scrollConnection != null) Modifier.nestedScroll(callbacks.scrollConnection) else Modifier,
                                    onScrollToBottom = callbacks.onScrollToBottom,
                                    onScrollDirectionChange = { },
                                    scrollToMessageId = scrollTargets[channel],
                                    onScrollToMessageHandle = { onClearScrollTarget(channel) },
                                    recoveryFabTooltipState = if (currentTourStep == TourStep.RecoveryFab) recoveryFabTooltipState else null,
                                    onTourAdvance = callbacks.onTourAdvance,
                                    onTourSkip = callbacks.onTourSkip,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
