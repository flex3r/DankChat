package com.flxrs.dankchat.ui.chat.replies

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.chat.UserLongClickBehavior
import com.flxrs.dankchat.ui.chat.BadgeUi
import com.flxrs.dankchat.ui.chat.ChatScreen
import com.flxrs.dankchat.ui.chat.ChatScreenCallbacks
import com.flxrs.dankchat.ui.chat.MessageTapContext
import com.flxrs.dankchat.ui.chat.MessageTapOperations
import com.flxrs.dankchat.ui.chat.emote.EmoteInfoViewModel
import com.flxrs.dankchat.ui.chat.message.MessageOptionsParams
import com.flxrs.dankchat.ui.chat.message.MessageOptionsViewModel
import com.flxrs.dankchat.ui.chat.message.rememberMessageCopyActions
import com.flxrs.dankchat.ui.chat.messageTapHandler
import com.flxrs.dankchat.ui.chat.user.UserPopupStateParams
import com.flxrs.dankchat.ui.chat.user.UserPopupViewModel
import com.flxrs.dankchat.ui.main.input.ChatInputViewModel
import com.flxrs.dankchat.ui.main.sheet.SheetNavigationViewModel
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RepliesComposable(
    repliesViewModel: RepliesViewModel,
    onMissing: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    scrollModifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onScrollToBottom: () -> Unit = {},
) {
    val emoteInfoViewModel: EmoteInfoViewModel = koinViewModel()
    val userPopupViewModel: UserPopupViewModel = koinViewModel()
    val messageOptionsViewModel: MessageOptionsViewModel = koinViewModel()
    val chatInputViewModel: ChatInputViewModel = koinViewModel()
    val sheetNavigationViewModel: SheetNavigationViewModel = koinViewModel()
    val chatSettingsDataStore: ChatSettingsDataStore = koinInject()
    val preferenceStore: DankChatPreferenceStore = koinInject()
    val displaySettings by repliesViewModel.chatDisplaySettings.collectAsStateWithLifecycle()
    val userLongClickBehavior by chatSettingsDataStore.userLongClickBehavior.collectAsStateWithLifecycle(initialValue = UserLongClickBehavior.MentionsUser)
    val messageTapAction by
        chatSettingsDataStore.messageTapAction.collectAsStateWithLifecycle(
            initialValue = chatSettingsDataStore.current().messageTapAction,
        )
    val messageCopyActions = rememberMessageCopyActions()
    val openUserCard: (String?, String, String, String?, List<BadgeUi>) -> Unit = { userId, userName, displayName, channel, badges ->
        userPopupViewModel.show(
            UserPopupStateParams(
                targetUserId = userId?.let { UserId(it) },
                targetUserName = UserName(userName),
                targetDisplayName = DisplayName(displayName),
                channel = channel?.let { UserName(it) },
                badges = badges.map { it.badge },
            ),
        )
    }
    val openMessageOptions: (String, String?, String) -> Unit = { messageId, channel, fullMessage ->
        messageOptionsViewModel.show(
            MessageOptionsParams(
                messageId = messageId,
                channel = channel?.let { UserName(it) },
                fullMessage = fullMessage,
                canModerate = false,
                canReply = false,
                canCopy = true,
                canJump = true,
            ),
        )
    }
    val whisperUser: (MessageTapContext) -> Unit = { message ->
        sheetNavigationViewModel.openWhispers()
        chatInputViewModel.setWhisperTarget(message.userName)
    }
    val onMessageTap =
        messageTapHandler(
            action = messageTapAction,
            isLoggedIn = preferenceStore.isLoggedIn,
            operations =
                MessageTapOperations(
                    reply = { message -> chatInputViewModel.setReplying(true, message.messageId, message.userName, message.message) },
                    mention = { message -> chatInputViewModel.mentionUser(message.userName, message.displayName) },
                    whisper = whisperUser,
                    openUserCard = { message ->
                        openUserCard(
                            message.userId?.value,
                            message.userName.value,
                            message.displayName.value,
                            message.channel?.value,
                            message.badges,
                        )
                    },
                    openMessageOptions = { message -> openMessageOptions(message.messageId, message.channel?.value, message.fullMessage) },
                    copyMessage = messageCopyActions.copyMessage,
                    copyFullMessage = messageCopyActions.copyFullMessage,
                ),
        )
    val uiState by repliesViewModel.uiState.collectAsStateWithLifecycle(initialValue = RepliesUiState.Found(persistentListOf()))

    when (uiState) {
        is RepliesUiState.Found -> {
            ChatScreen(
                messages = (uiState as RepliesUiState.Found).items,
                fontSize = displaySettings.fontSize,
                callbacks =
                    ChatScreenCallbacks(
                        onUserClick = { userId, userName, displayName, channel, badges, isLongPress ->
                            val shouldOpenPopup =
                                when (userLongClickBehavior) {
                                    UserLongClickBehavior.MentionsUser -> !isLongPress
                                    UserLongClickBehavior.OpensPopup -> isLongPress
                                }
                            if (shouldOpenPopup) {
                                openUserCard(userId, userName, displayName, channel, badges)
                            } else {
                                chatInputViewModel.mentionUser(UserName(userName), DisplayName(displayName))
                            }
                        },
                        onMessageLongClick = openMessageOptions,
                        onEmoteClick = { emoteInfoViewModel.show(it) },
                        onMessageTap = onMessageTap,
                    ),
                animateGifs = displaySettings.animateGifs,
                modifier = modifier,
                contentPadding = contentPadding,
                scrollModifier = scrollModifier,
                containerColor = containerColor,
                onScrollToBottom = onScrollToBottom,
            )
        }

        is RepliesUiState.NotFound -> {
            LaunchedEffect(Unit) {
                onMissing()
            }
        }
    }
}
