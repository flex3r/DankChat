package com.flxrs.dankchat.ui.chat

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledScrollArea
import com.composeunstyled.UnstyledThumb
import com.composeunstyled.UnstyledVerticalScrollbar
import com.composeunstyled.rememberScrollAreaState
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.preferences.appearance.FabAnchor
import com.flxrs.dankchat.preferences.appearance.InputAction
import com.flxrs.dankchat.ui.chat.emote.EmoteSheetData
import com.flxrs.dankchat.ui.chat.messages.AutomodMessageComposable
import com.flxrs.dankchat.ui.chat.messages.DateSeparatorComposable
import com.flxrs.dankchat.ui.chat.messages.ModerationMessageComposable
import com.flxrs.dankchat.ui.chat.messages.NoticeMessageComposable
import com.flxrs.dankchat.ui.chat.messages.PointRedemptionMessageComposable
import com.flxrs.dankchat.ui.chat.messages.PrivMessageComposable
import com.flxrs.dankchat.ui.chat.messages.SystemMessageComposable
import com.flxrs.dankchat.ui.chat.messages.UserNoticeMessageComposable
import com.flxrs.dankchat.ui.chat.messages.WhisperMessageComposable
import com.flxrs.dankchat.ui.main.TheaterChatModeIcon
import com.flxrs.dankchat.ui.main.input.TourTooltip
import com.flxrs.dankchat.utils.compose.predictiveBackScale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

data class ChatScreenCallbacks(
    val onUserClick: (userId: String?, userName: String, displayName: String, channel: String?, badges: List<BadgeUi>, isLongPress: Boolean) -> Unit,
    val onMessageLongClick: (messageId: String, channel: String?, fullMessage: String) -> Unit,
    val onEmoteClick: (emotes: List<EmoteSheetData>) -> Unit = {},
    val onReplyClick: (rootMessageId: String, replyName: UserName) -> Unit = { _, _ -> },
    val onWhisperReply: ((userName: UserName) -> Unit)? = null,
    val onAutomodAllow: (heldMessageId: String, channel: UserName) -> Unit = { _, _ -> },
    val onAutomodDeny: (heldMessageId: String, channel: UserName) -> Unit = { _, _ -> },
    val onAutomodBanUser: (messageId: String, channel: String?, fullMessage: String) -> Unit = { _, _, _ -> },
)

@Immutable
data class ChatScrollPosition(
    val firstVisibleItemId: String? = null,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
    val shouldAutoScroll: Boolean = true,
)

internal fun <T> ChatScrollPosition.resolveFirstVisibleItemIndex(
    items: List<T>,
    itemId: (T) -> String,
): Int {
    val anchoredIndex = firstVisibleItemId?.let { anchor -> items.indexOfFirst { itemId(it) == anchor } } ?: -1
    return when {
        anchoredIndex >= 0 -> anchoredIndex
        items.isEmpty() -> 0
        else -> firstVisibleItemIndex.coerceIn(items.indices)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: ImmutableList<ChatMessageUiState>,
    fontSize: Float,
    callbacks: ChatScreenCallbacks,
    modifier: Modifier = Modifier,
    scrollModifier: Modifier = Modifier,
    initialScrollPosition: ChatScrollPosition = ChatScrollPosition(),
    trackScrollPosition: Boolean = false,
    onScrollPositionChange: (ChatScrollPosition) -> Unit = {},
    showChannelPrefix: Boolean = false,
    animateGifs: Boolean = true,
    showInput: Boolean = true,
    isFullscreen: Boolean = false,
    fullscreenButtonOpacity: Float = 0.75f,
    requireFullscreenExitConfirmation: Boolean = false,
    fabAnchor: FabAnchor = FabAnchor.BottomEnd,
    fabOffsetXFraction: Float = 0f,
    fabOffsetYFraction: Float = 0f,
    onFabPositionChange: (FabAnchor, Float, Float) -> Unit = { _, _, _ -> },
    onRecover: () -> Unit = {},
    fabMenuCallbacks: FabMenuCallbacks? = null,
    showTheaterChatModeFab: Boolean = false,
    isTheaterChatDocked: Boolean = false,
    onToggleTheaterChatMode: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    onScrollToBottom: () -> Unit = {},
    onScrollDirectionChange: (isScrollingUp: Boolean) -> Unit = {},
    scrollToMessageId: String? = null,
    onScrollToMessageHandle: () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    showFabs: Boolean = true,
    recoveryFabTooltipState: TooltipState? = null,
    onTourAdvance: (() -> Unit)? = null,
    onTourSkip: (() -> Unit)? = null,
) {
    val reversedMessages = messages.asReversed()
    val currentReversedMessages by rememberUpdatedState(reversedMessages)
    val initialFirstVisibleItemIndex =
        remember {
            initialScrollPosition.resolveFirstVisibleItemIndex(
                items = reversedMessages,
                itemId = ChatMessageUiState::id,
            )
        }
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = initialScrollPosition.firstVisibleItemScrollOffset,
        )

    // Track if we should auto-scroll to bottom (sticky state)
    var shouldAutoScroll by rememberSaveable { mutableStateOf(initialScrollPosition.shouldAutoScroll) }

    LaunchedEffect(trackScrollPosition, listState) {
        if (!trackScrollPosition) return@LaunchedEffect
        snapshotFlow {
            if (listState.isScrollInProgress) {
                null
            } else {
                ChatScrollPosition(
                    firstVisibleItemId = currentReversedMessages.getOrNull(listState.firstVisibleItemIndex)?.id,
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                    shouldAutoScroll = shouldAutoScroll,
                )
            }
        }.filterNotNull()
            .distinctUntilChanged()
            .collect(onScrollPositionChange)
    }

    // Detect if we're showing the newest messages (with reverseLayout, index 0 = newest).
    // Require zero scroll offset so items scrolled into the bottom content padding
    // (behind the input bar) don't count as "at bottom".
    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        }
    }

    // Disable auto-scroll when user scrolls up, re-enable when they return to bottom
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.lastScrolledForward && shouldAutoScroll) {
            shouldAutoScroll = false
        }
        if (!listState.isScrollInProgress && isAtBottom && !shouldAutoScroll) {
            shouldAutoScroll = true
        }
        onScrollDirectionChange(listState.lastScrolledForward)
    }

    // Auto-scroll when new messages arrive or when re-enabled, keyed on the newest message id
    // so emissions that only rewrite or prepend messages don't restart the effect
    LaunchedEffect(shouldAutoScroll, messages.lastOrNull()?.id) {
        if (shouldAutoScroll) {
            listState.scrollToItem(0)
        }
    }

    // Handle scroll-to-message requests — keyed on both scrollToMessageId and whether messages
    // are available, so the scroll retries after ViewModel recreation (which briefly empties messages).
    val hasMessages = reversedMessages.isNotEmpty()
    val density = LocalDensity.current
    LaunchedEffect(scrollToMessageId, hasMessages) {
        val targetId = scrollToMessageId ?: return@LaunchedEffect
        if (!hasMessages) return@LaunchedEffect
        val index = reversedMessages.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            shouldAutoScroll = false
            val topPaddingPx = with(density) { contentPadding.calculateTopPadding().roundToPx() }
            val bottomPaddingPx = with(density) { contentPadding.calculateBottomPadding().roundToPx() }
            listState.scrollToCentered(index, topPaddingPx, bottomPaddingPx)
        }
        onScrollToMessageHandle()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = containerColor,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + MESSAGE_GAP,
                    bottom = contentPadding.calculateBottomPadding() + MESSAGE_GAP,
                ),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(scrollModifier),
            ) {
                itemsIndexed(
                    items = reversedMessages,
                    key = { _, message -> message.id },
                    contentType = { _, message ->
                        when (message) {
                            is ChatMessageUiState.SystemMessageUi -> "system"
                            is ChatMessageUiState.NoticeMessageUi -> "notice"
                            is ChatMessageUiState.UserNoticeMessageUi -> "usernotice"
                            is ChatMessageUiState.ModerationMessageUi -> "moderation"
                            is ChatMessageUiState.AutomodMessageUi -> "automod"
                            is ChatMessageUiState.PrivMessageUi -> "privmsg"
                            is ChatMessageUiState.WhisperMessageUi -> "whisper"
                            is ChatMessageUiState.PointRedemptionMessageUi -> "redemption"
                            is ChatMessageUiState.DateSeparatorUi -> "datesep"
                        }
                    },
                ) { _, message ->
                    Box {
                        ChatMessageItem(
                            message = message,
                            highlightShape = message.toHighlightShape(),
                            fontSize = fontSize,
                            showChannelPrefix = showChannelPrefix,
                            animateGifs = animateGifs,
                            callbacks = callbacks,
                        )

                        if (message.showDividerBelow) {
                            val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            HorizontalDivider(
                                modifier = Modifier.align(Alignment.BottomCenter),
                                color = dividerColor,
                            )
                        }
                    }
                }
            }

            // FABs at bottom-end with coordinated position animation
            if (showFabs) {
                val showScrollFab = !shouldAutoScroll && messages.isNotEmpty()
                // Scaffold padding already animates with input show/hide; extra spring would double up.
                val fabBottomPadding = contentPadding.calculateBottomPadding()
                val recoveryBottomPadding by animateDpAsState(
                    targetValue = if (showScrollFab) 56.dp + 12.dp else 0.dp,
                    label = "recoveryBottomPadding",
                )
                var fabMenuExpanded by remember { mutableStateOf(false) }

                // Dismiss scrim + back handler when fab menu is open
                if (fabMenuExpanded) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) {
                                    fabMenuExpanded = false
                                },
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp + fabBottomPadding),
                ) {
                    AnimatedVisibility(
                        visible = showScrollFab,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                    ) {
                        FloatingActionButton(
                            onClick = {
                                shouldAutoScroll = true
                                onScrollDirectionChange(false)
                                onScrollToBottom()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                            )
                        }
                    }
                }
                FabCluster(
                    anchor = fabAnchor,
                    offsetXFraction = fabOffsetXFraction,
                    offsetYFraction = fabOffsetYFraction,
                    onPositionChange = onFabPositionChange,
                    topSystemInset = contentPadding.calculateTopPadding(),
                    bottomSystemInset = fabBottomPadding,
                    stackOffsetAtBottomEnd = { recoveryBottomPadding },
                    dragEnabled = recoveryFabTooltipState == null,
                ) { dragModifier, isDragging ->
                    RecoveryFabs(
                        isFullscreen = isFullscreen,
                        showInput = showInput,
                        isDragging = isDragging,
                        fullscreenButtonOpacity = fullscreenButtonOpacity,
                        requireConfirmation = requireFullscreenExitConfirmation,
                        onRecover = onRecover,
                        fabMenuCallbacks = fabMenuCallbacks,
                        showTheaterChatModeFab = showTheaterChatModeFab,
                        isTheaterChatDocked = isTheaterChatDocked,
                        onToggleTheaterChatMode = onToggleTheaterChatMode,
                        menuExpanded = fabMenuExpanded,
                        onMenuExpandedChange = { fabMenuExpanded = it },
                        recoveryFabTooltipState = recoveryFabTooltipState,
                        onTourAdvance = {
                            onTourAdvance?.invoke()
                            onRecover()
                        },
                        onTourSkip = {
                            onTourSkip?.invoke()
                            onRecover()
                        },
                        dragModifier = dragModifier,
                    )
                }
            }
        }
    }
}

@Immutable
data class FabMenuCallbacks(
    val onAction: (InputAction) -> Unit,
    val onAudioOnly: () -> Unit,
    val isStreamActive: Boolean,
    val isAudioOnly: Boolean,
    val hasStreamData: Boolean,
    val isFullscreen: Boolean,
    val isModerator: Boolean,
    val debugMode: Boolean,
    val enabled: Boolean,
    val hasLastMessage: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryFabs(
    isFullscreen: Boolean,
    showInput: Boolean,
    fullscreenButtonOpacity: Float,
    onRecover: () -> Unit,
    fabMenuCallbacks: FabMenuCallbacks?,
    showTheaterChatModeFab: Boolean,
    isTheaterChatDocked: Boolean,
    onToggleTheaterChatMode: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
    isDragging: Boolean = false,
    requireConfirmation: Boolean = false,
    recoveryFabTooltipState: TooltipState? = null,
    onTourAdvance: (() -> Unit)? = null,
    onTourSkip: (() -> Unit)? = null,
) {
    val visible = isFullscreen || !showInput
    val dragElevation = if (isDragging) 12.dp else 0.dp
    val idleContainer = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = fullscreenButtonOpacity)
    val dragContainer = MaterialTheme.colorScheme.primary
    val idleContent = MaterialTheme.colorScheme.onSecondaryContainer
    val dragContent = MaterialTheme.colorScheme.onPrimary
    val animatedContainer by animateColorAsState(
        targetValue = if (isDragging) dragContainer else idleContainer,
        animationSpec = tween(150),
        label = "fabDragContainer",
    )
    val animatedContent by animateColorAsState(
        targetValue = if (isDragging) dragContent else idleContent,
        animationSpec = tween(150),
        label = "fabDragContent",
    )
    val isTourHighlighted = recoveryFabTooltipState != null
    val fabShape = FloatingActionButtonDefaults.smallShape

    var confirmPending by remember { mutableStateOf(false) }
    var pendingLabel by remember { mutableStateOf("") }
    LaunchedEffect(visible, requireConfirmation) {
        if (!visible || !requireConfirmation) confirmPending = false
    }
    LaunchedEffect(confirmPending) {
        if (confirmPending) {
            kotlinx.coroutines.delay(3000)
            confirmPending = false
        }
    }

    val commitRecover: () -> Unit = {
        confirmPending = false
        onMenuExpandedChange(false)
        onTourAdvance?.invoke()
        onRecover()
    }
    val actionLabel = when {
        isFullscreen -> stringResource(R.string.menu_exit_fullscreen)
        else -> stringResource(R.string.menu_show_input)
    }
    // Snapshotted at tap-time so the exit animation doesn't flash the next-mode label.
    val chipLabel = pendingLabel.ifEmpty { actionLabel }

    val escapeFab: @Composable () -> Unit = {
        AnimatedContent(
            targetState = confirmPending,
            transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            label = "EscapeFabConfirm",
        ) { pending ->
            when {
                pending -> ExtendedFloatingActionButton(
                    onClick = commitRecover,
                    containerColor = animatedContainer,
                    contentColor = animatedContent,
                    elevation = FloatingActionButtonDefaults.elevation(dragElevation, dragElevation, dragElevation, dragElevation),
                    icon = { Icon(Icons.Default.FullscreenExit, contentDescription = null) },
                    text = { Text(chipLabel) },
                )

                else -> SmallFloatingActionButton(
                    onClick = {
                        when {
                            requireConfirmation && !isTourHighlighted -> {
                                pendingLabel = actionLabel
                                confirmPending = true
                            }

                            else -> commitRecover()
                        }
                    },
                    containerColor = when {
                        isTourHighlighted -> MaterialTheme.colorScheme.secondaryContainer
                        else -> animatedContainer
                    },
                    contentColor = animatedContent,
                    elevation = FloatingActionButtonDefaults.elevation(dragElevation, dragElevation, dragElevation, dragElevation),
                    modifier = when {
                        isTourHighlighted -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, fabShape)
                        else -> Modifier
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = actionLabel,
                    )
                }
            }
        }
    }

    if (recoveryFabTooltipState != null) {
        TooltipBox(
            positionProvider =
                TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above,
                    spacingBetweenTooltipAndAnchor = 8.dp,
                ),
            tooltip = {
                TourTooltip(
                    text = stringResource(R.string.tour_recovery_fab),
                    onAction = { onTourAdvance?.invoke() },
                    onSkip = { onTourSkip?.invoke() },
                    isLast = true,
                    showCaret = false,
                )
            },
            state = recoveryFabTooltipState,
            onDismissRequest = {},
            hasAction = true,
            modifier = modifier,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                escapeFab()
            }
        }
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = modifier,
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // Chip expansion changes cluster size; disable drag to keep origin math correct.
                modifier = if (confirmPending) Modifier else dragModifier,
            ) {
                if (!showInput && fabMenuCallbacks != null) {
                    FabMenuToggle(
                        fabMenuCallbacks = fabMenuCallbacks,
                        menuExpanded = menuExpanded,
                        onMenuExpandedChange = onMenuExpandedChange,
                        containerColor = animatedContainer,
                        contentColor = animatedContent,
                        dragElevation = dragElevation,
                    )
                }
                if (!showInput && showTheaterChatModeFab) {
                    SmallFloatingActionButton(
                        onClick = onToggleTheaterChatMode,
                        containerColor = animatedContainer,
                        contentColor = animatedContent,
                        elevation = FloatingActionButtonDefaults.elevation(dragElevation, dragElevation, dragElevation, dragElevation),
                    ) {
                        TheaterChatModeIcon(isDocked = isTheaterChatDocked)
                    }
                }
                escapeFab()
            }
        }
    }
}

@Composable
private fun FabMenuToggle(
    fabMenuCallbacks: FabMenuCallbacks,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    containerColor: Color,
    contentColor: Color,
    dragElevation: Dp = 0.dp,
) {
    AnimatedContent(
        targetState = menuExpanded,
        transitionSpec = {
            (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut())
        },
        label = "FabMenuToggle",
    ) { expanded ->
        when {
            expanded -> {
                var backProgress by remember { mutableFloatStateOf(0f) }
                PredictiveBackHandler { progress ->
                    try {
                        progress.collect { event ->
                            backProgress = event.progress
                        }
                        onMenuExpandedChange(false)
                    } catch (_: Exception) {
                        backProgress = 0f
                    }
                }
                FabActionsMenu(
                    callbacks = fabMenuCallbacks,
                    onDismiss = { onMenuExpandedChange(false) },
                    modifier = Modifier.predictiveBackScale(backProgress),
                )
            }

            else -> {
                SmallFloatingActionButton(
                    onClick = { onMenuExpandedChange(true) },
                    containerColor = containerColor,
                    contentColor = contentColor,
                    elevation = FloatingActionButtonDefaults.elevation(dragElevation, dragElevation, dragElevation, dragElevation),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more),
                    )
                }
            }
        }
    }
}

@Composable
private fun FabActionsMenu(
    callbacks: FabMenuCallbacks,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val windowHeight =
        with(density) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val menuMaxHeight = windowHeight * 0.35f
    val scrollState = rememberScrollState()
    var itemHeightPx by remember { mutableIntStateOf(0) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
        modifier = modifier.heightIn(max = menuMaxHeight),
    ) {
        UnstyledScrollArea(state = rememberScrollAreaState(scrollState)) {
            Column(
                modifier =
                    Modifier
                        .width(IntrinsicSize.Max)
                        .verticalScroll(scrollState),
            ) {
                val menuItems =
                    InputAction.entries.mapNotNull { action ->
                        getFabMenuItem(
                            action = action,
                            isStreamActive = callbacks.isStreamActive,
                            hasStreamData = callbacks.hasStreamData,
                            isFullscreen = callbacks.isFullscreen,
                            isModerator = callbacks.isModerator,
                            debugMode = callbacks.debugMode,
                        )?.let { action to it }
                    }

                menuItems.forEachIndexed { index, (action, item) ->
                    val actionEnabled =
                        when (action) {
                            InputAction.Search, InputAction.Fullscreen, InputAction.HideInput, InputAction.Debug -> true
                            InputAction.LastMessage -> callbacks.enabled && callbacks.hasLastMessage
                            InputAction.Stream, InputAction.ModActions, InputAction.Theater -> callbacks.enabled
                        }

                    val measureModifier = if (index == 0) {
                        Modifier.onSizeChanged { itemHeightPx = it.height }
                    } else {
                        Modifier
                    }

                    DropdownMenuItem(
                        text = { Text(stringResource(item.labelRes)) },
                        onClick = {
                            callbacks.onAction(action)
                            onDismiss()
                        },
                        enabled = actionEnabled,
                        modifier = measureModifier,
                        leadingIcon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                            )
                        },
                    )
                }

                if (callbacks.isStreamActive) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (callbacks.isAudioOnly) R.string.menu_exit_audio_only else R.string.menu_audio_only,
                                ),
                            )
                        },
                        onClick = {
                            callbacks.onAudioOnly()
                            onDismiss()
                        },
                        enabled = callbacks.enabled,
                        leadingIcon = {
                            Icon(
                                imageVector = if (callbacks.isAudioOnly) Icons.Default.Videocam else Icons.Default.Headphones,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
            if (scrollState.maxValue > itemHeightPx) {
                UnstyledVerticalScrollbar(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxHeight()
                            .width(3.dp)
                            .padding(vertical = 2.dp),
                ) {
                    UnstyledThumb(
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
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
private data class FabMenuItem(
    val labelRes: Int,
    val icon: ImageVector,
)

private fun getFabMenuItem(
    action: InputAction,
    isStreamActive: Boolean,
    hasStreamData: Boolean,
    isFullscreen: Boolean,
    isModerator: Boolean,
    debugMode: Boolean,
): FabMenuItem? = when (action) {
    InputAction.Search -> {
        FabMenuItem(R.string.input_action_search, Icons.Default.Search)
    }

    InputAction.LastMessage -> {
        null
    }

    InputAction.Stream -> {
        when {
            hasStreamData || isStreamActive -> {
                FabMenuItem(
                    if (isStreamActive) R.string.menu_hide_stream else R.string.menu_show_stream,
                    if (isStreamActive) Icons.Default.VideocamOff else Icons.Default.Videocam,
                )
            }

            else -> {
                null
            }
        }
    }

    InputAction.ModActions -> {
        when {
            isModerator -> FabMenuItem(R.string.menu_mod_actions, Icons.Default.Shield)
            else -> null
        }
    }

    InputAction.Fullscreen -> {
        FabMenuItem(
            if (isFullscreen) R.string.menu_exit_fullscreen else R.string.menu_fullscreen,
            if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
        )
    }

    InputAction.Theater -> {
        when {
            isStreamActive -> FabMenuItem(R.string.menu_theater_mode, Icons.Default.Theaters)
            else -> null
        }
    }

    InputAction.HideInput -> {
        FabMenuItem(R.string.menu_show_input, Icons.Default.Visibility)
    }

    InputAction.Debug -> {
        when {
            debugMode -> FabMenuItem(R.string.input_action_debug, Icons.Default.BugReport)
            else -> null
        }
    }
}

private val MESSAGE_GAP = 4.dp
private val HIGHLIGHT_CORNER_RADIUS = 6.dp

private fun ChatMessageUiState.toHighlightShape(): Shape {
    if (!isHighlighted) return RectangleShape
    val top = if (roundedTopCorners) HIGHLIGHT_CORNER_RADIUS else 0.dp
    val bottom = if (roundedBottomCorners) HIGHLIGHT_CORNER_RADIUS else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * Renders a single chat message based on its type
 */

@Composable
private fun ChatMessageItem(
    message: ChatMessageUiState,
    highlightShape: Shape,
    fontSize: Float,
    showChannelPrefix: Boolean,
    animateGifs: Boolean,
    callbacks: ChatScreenCallbacks,
) {
    when (message) {
        is ChatMessageUiState.SystemMessageUi -> {
            SystemMessageComposable(
                message = message,
                fontSize = fontSize,
            )
        }

        is ChatMessageUiState.NoticeMessageUi -> {
            NoticeMessageComposable(
                message = message,
                fontSize = fontSize,
            )
        }

        is ChatMessageUiState.UserNoticeMessageUi -> {
            UserNoticeMessageComposable(
                message = message,
                highlightShape = highlightShape,
                fontSize = fontSize,
                animateGifs = animateGifs,
                onUserClick = callbacks.onUserClick,
                onMessageLongClick = callbacks.onMessageLongClick,
            )
        }

        is ChatMessageUiState.ModerationMessageUi -> {
            ModerationMessageComposable(
                message = message,
                fontSize = fontSize,
                animateGifs = animateGifs,
                onUserClick = callbacks.onUserClick,
                showChannelPrefix = showChannelPrefix,
            )
        }

        is ChatMessageUiState.AutomodMessageUi -> {
            AutomodMessageComposable(
                message = message,
                fontSize = fontSize,
                onAllow = callbacks.onAutomodAllow,
                onDeny = callbacks.onAutomodDeny,
                onMessageLongClick = {
                    callbacks.onMessageLongClick(message.id, message.channel.value, message.messageText.orEmpty())
                },
                onBanUser = {
                    callbacks.onAutomodBanUser(message.id, message.channel.value, message.messageText.orEmpty())
                },
            )
        }

        is ChatMessageUiState.PrivMessageUi -> {
            PrivMessageComposable(
                message = message,
                highlightShape = highlightShape,
                fontSize = fontSize,
                showChannelPrefix = showChannelPrefix,
                animateGifs = animateGifs,
                onUserClick = callbacks.onUserClick,
                onMessageLongClick = callbacks.onMessageLongClick,
                onEmoteClick = callbacks.onEmoteClick,
                onReplyClick = callbacks.onReplyClick,
            )
        }

        is ChatMessageUiState.PointRedemptionMessageUi -> {
            PointRedemptionMessageComposable(
                message = message,
                highlightShape = highlightShape,
                fontSize = fontSize,
            )
        }

        is ChatMessageUiState.DateSeparatorUi -> {
            DateSeparatorComposable(
                message = message,
                fontSize = fontSize,
            )
        }

        is ChatMessageUiState.WhisperMessageUi -> {
            WhisperMessageComposable(
                message = message,
                fontSize = fontSize,
                animateGifs = animateGifs,
                onUserClick = { userId, userName, displayName, badges, isLongPress ->
                    callbacks.onUserClick(userId, userName, displayName, null, badges, isLongPress)
                },
                onMessageLongClick = { messageId, fullMessage ->
                    callbacks.onMessageLongClick(messageId, null, fullMessage)
                },
                onEmoteClick = callbacks.onEmoteClick,
                onWhisperReply = callbacks.onWhisperReply,
            )
        }
    }
}

/**
 * Scrolls so that [index] is vertically centered in the usable viewport area
 * (the region between [topPaddingPx] and [bottomPaddingPx]).
 *
 * Works in two instant steps that coalesce into a single visual frame:
 * 1. [scrollToItem] ensures the target item is laid out and measurable.
 * 2. Reads the item's actual position, computes the delta needed to center it,
 *    and applies the correction via [scroll].
 */
private suspend fun LazyListState.scrollToCentered(
    index: Int,
    topPaddingPx: Int,
    bottomPaddingPx: Int,
) {
    scrollToItem(index)

    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportHeight = layoutInfo.viewportSize.height
    val usableBottom = viewportHeight - bottomPaddingPx
    val usableCenter = (topPaddingPx + usableBottom) / 2
    val itemCenter = itemInfo.offset + itemInfo.size / 2
    val delta = (itemCenter - usableCenter).toFloat()

    scroll { scrollBy(delta) }
}
