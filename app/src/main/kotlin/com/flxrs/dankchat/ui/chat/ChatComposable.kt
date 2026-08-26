package com.flxrs.dankchat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TooltipState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.chat.UserLongClickBehavior
import com.flxrs.dankchat.ui.chat.emote.EmoteInfoViewModel
import com.flxrs.dankchat.ui.chat.message.MessageOptionsParams
import com.flxrs.dankchat.ui.chat.message.MessageOptionsViewModel
import com.flxrs.dankchat.ui.chat.message.MessageReplyAction
import com.flxrs.dankchat.ui.chat.user.UserPopupStateParams
import com.flxrs.dankchat.ui.chat.user.UserPopupViewModel
import com.flxrs.dankchat.ui.main.input.ChatInputViewModel
import com.flxrs.dankchat.ui.main.sheet.SheetNavigationViewModel
import kotlinx.collections.immutable.ImmutableList
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatComposable(
    channel: UserName,
    onReplyClick: (String, UserName) -> Unit,
    modifier: Modifier = Modifier,
    scrollModifier: Modifier = Modifier,
    isCollectionActive: Boolean = true,
    isPageVisible: Boolean = true,
    showInput: Boolean = true,
    isFullscreen: Boolean = false,
    showFabs: Boolean = true,
    onRecover: () -> Unit = {},
    fabMenuCallbacks: FabMenuCallbacks? = null,
    showPinnedMessage: Boolean = true,
    isToolbarMenuOpen: Boolean = false,
    showTheaterChatModeFab: Boolean = false,
    isTheaterChatDocked: Boolean = false,
    onToggleTheaterChatMode: () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentPadding: PaddingValues = PaddingValues(),
    onScrollToBottom: () -> Unit = {},
    onScrollDirectionChange: (Boolean) -> Unit = {},
    scrollToMessageId: String? = null,
    onScrollToMessageHandle: () -> Unit = {},
    recoveryFabTooltipState: TooltipState? = null,
    onTourAdvance: (() -> Unit)? = null,
    onTourSkip: (() -> Unit)? = null,
) {
    val viewModel: ChatViewModel =
        koinViewModel(
            key = channel.value,
            parameters = { parametersOf(channel) },
        )
    val pinnedMessageViewModel: PinnedMessageViewModel =
        koinViewModel(
            key = "pinned-${channel.value}",
            parameters = { parametersOf(channel) },
        )
    val emoteInfoViewModel: EmoteInfoViewModel = koinViewModel()
    val userPopupViewModel: UserPopupViewModel = koinViewModel()
    val messageOptionsViewModel: MessageOptionsViewModel = koinViewModel()
    val chatInputViewModel: ChatInputViewModel = koinViewModel()
    val sheetNavigationViewModel: SheetNavigationViewModel = koinViewModel()
    val chatSettingsDataStore: ChatSettingsDataStore = koinInject()
    val preferenceStore: DankChatPreferenceStore = koinInject()

    if (!isCollectionActive) return

    // Offscreen pages keep their last list instead of collecting, so incoming messages on
    // neighbor channels cost nothing while the page is composed but not visible
    val frozenMessages = remember { mutableStateOf(viewModel.chatUiStates.value) }
    val messages: ImmutableList<ChatMessageUiState>
    if (isPageVisible) {
        messages = viewModel.chatUiStates.collectAsStateWithLifecycle().value
        frozenMessages.value = messages
    } else {
        messages = frozenMessages.value
    }
    val displaySettings by viewModel.chatDisplaySettings.collectAsStateWithLifecycle()
    val userLongClickBehavior by chatSettingsDataStore.userLongClickBehavior.collectAsStateWithLifecycle(initialValue = UserLongClickBehavior.MentionsUser)
    val isLoggedIn = preferenceStore.isLoggedIn

    val callbacks =
        ChatScreenCallbacks(
            onUserClick = { userId, userName, displayName, ch, badges, isLongPress ->
                val shouldOpenPopup =
                    when (userLongClickBehavior) {
                        UserLongClickBehavior.MentionsUser -> !isLongPress
                        UserLongClickBehavior.OpensPopup -> isLongPress
                    }
                if (shouldOpenPopup) {
                    userPopupViewModel.show(
                        UserPopupStateParams(
                            targetUserId = userId?.let { UserId(it) },
                            targetUserName = UserName(userName),
                            targetDisplayName = DisplayName(displayName),
                            channel = ch?.let { UserName(it) },
                            badges = badges.map { it.badge },
                        ),
                    )
                } else {
                    chatInputViewModel.mentionUser(UserName(userName), DisplayName(displayName))
                }
            },
            onMessageLongClick = { messageId, ch, fullMessage ->
                messageOptionsViewModel.show(
                    MessageOptionsParams(
                        messageId = messageId,
                        channel = ch?.let { UserName(it) },
                        fullMessage = fullMessage,
                        canModerate = isLoggedIn,
                        canCopy = true,
                        replyAction = MessageReplyAction.Channel.takeIf { isLoggedIn },
                    ),
                )
            },
            onWhisperLongClick = { messageId, fullMessage, replyTarget ->
                messageOptionsViewModel.show(
                    MessageOptionsParams(
                        messageId = messageId,
                        channel = null,
                        fullMessage = fullMessage,
                        canModerate = false,
                        canCopy = true,
                        replyAction = MessageReplyAction.Whisper(replyTarget).takeIf { isLoggedIn },
                    ),
                )
            },
            onEmoteClick = { emoteInfoViewModel.show(it) },
            onReplyClick = onReplyClick,
            onWhisperReply = { target ->
                sheetNavigationViewModel.openWhispers()
                chatInputViewModel.setWhisperTarget(target)
            },
            onAutomodAllow = { heldMessageId, ch -> viewModel.manageAutomodMessage(heldMessageId, ch, allow = true) },
            onAutomodDeny = { heldMessageId, ch -> viewModel.manageAutomodMessage(heldMessageId, ch, allow = false) },
            onAutomodBanUser = { messageId, ch, fullMessage ->
                messageOptionsViewModel.show(
                    MessageOptionsParams(
                        messageId = messageId,
                        channel = ch?.let { UserName(it) },
                        fullMessage = fullMessage,
                        canModerate = isLoggedIn,
                        canCopy = false,
                        startWithBan = true,
                    ),
                )
            },
        )

    Box(modifier = modifier.fillMaxSize()) {
        ChatScreen(
            messages = messages,
            fontSize = displaySettings.fontSize,
            callbacks = callbacks,
            animateGifs = displaySettings.animateGifs,
            fullscreenButtonOpacity = displaySettings.fullscreenButtonOpacity,
            requireFullscreenExitConfirmation = displaySettings.requireFullscreenExitConfirmation,
            fabAnchor = displaySettings.fabAnchor,
            fabOffsetXFraction = displaySettings.fabOffsetXFraction,
            fabOffsetYFraction = displaySettings.fabOffsetYFraction,
            onFabPositionChange = viewModel::persistFabPosition,
            modifier = Modifier.fillMaxSize(),
            showInput = showInput,
            isFullscreen = isFullscreen,
            showFabs = showFabs,
            onRecover = onRecover,
            fabMenuCallbacks = fabMenuCallbacks,
            showTheaterChatModeFab = showTheaterChatModeFab,
            isTheaterChatDocked = isTheaterChatDocked,
            onToggleTheaterChatMode = onToggleTheaterChatMode,
            containerColor = containerColor,
            contentPadding = contentPadding,
            scrollModifier = scrollModifier,
            onScrollToBottom = onScrollToBottom,
            onScrollDirectionChange = onScrollDirectionChange,
            scrollToMessageId = scrollToMessageId,
            onScrollToMessageHandle = onScrollToMessageHandle,
            recoveryFabTooltipState = recoveryFabTooltipState,
            onTourAdvance = onTourAdvance,
            onTourSkip = onTourSkip,
        )

        // Compact window heights (landscape phones) have no room for the banner, collapse it into
        // the toolbar pin icon; it can still be expanded manually and new pins still pop up
        val isCompactHeightWindow =
            !currentWindowAdaptiveInfo().windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
        LaunchedEffect(isCompactHeightWindow) {
            if (isCompactHeightWindow) {
                pinnedMessageViewModel.collapse()
            }
        }

        val pinnedState by pinnedMessageViewModel.uiState.collectAsStateWithLifecycle()
        val expandedPinnedState = pinnedState as? PinnedMessageUiState.Expanded
        // Keep the last expanded state around so the exit animation has content to render
        val lastExpandedPinnedState = remember { mutableStateOf(expandedPinnedState) }
        if (expandedPinnedState != null) {
            lastExpandedPinnedState.value = expandedPinnedState
        }
        val bannerTopPadding = contentPadding.calculateTopPadding() + 4.dp
        val bannerSlideOffsetPx = with(LocalDensity.current) { bannerTopPadding.roundToPx() }
        // Outer visibility follows the toolbar and slides all the way off-screen so both move as
        // one group, inner visibility follows the pin state and collapses in place
        AnimatedVisibility(
            visible = showPinnedMessage,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = bannerTopPadding)
                    .padding(horizontal = 8.dp),
            enter = slideInVertically(initialOffsetY = { -(it + bannerSlideOffsetPx) }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -(it + bannerSlideOffsetPx) }) + fadeOut(),
        ) {
            AnimatedVisibility(
                visible = expandedPinnedState != null && !isToolbarMenuOpen,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                val bannerState = expandedPinnedState ?: lastExpandedPinnedState.value
                if (bannerState != null) {
                    PinnedMessageBanner(
                        state = bannerState,
                        fontSize = displaySettings.fontSize,
                        animateGifs = displaySettings.animateGifs,
                        callbacks = callbacks,
                        onCollapse = pinnedMessageViewModel::toggleExpanded,
                        onUnpin = pinnedMessageViewModel::unpin,
                    )
                }
            }
        }
    }
}
