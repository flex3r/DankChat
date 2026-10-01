package com.flxrs.dankchat.ui.chat.mention

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.chat.MessageTapAction
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
fun MentionComposable(
    mentionViewModel: MentionViewModel,
    isWhisperTab: Boolean,
    containerColor: Color,
    modifier: Modifier = Modifier,
    scrollModifier: Modifier = Modifier,
    onWhisperReply: ((userName: UserName) -> Unit)? = null,
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
    val displaySettings by mentionViewModel.chatDisplaySettings.collectAsStateWithLifecycle()
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
                    reply = { message ->
                        if (message.isWhisper) {
                            onWhisperReply?.invoke(message.userName)
                        }
                    },
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
    val canTapMentions =
        messageTapAction != MessageTapAction.Reply &&
            messageTapAction != MessageTapAction.Mention
    val messages by when {
        isWhisperTab -> mentionViewModel.whispersUiStates.collectAsStateWithLifecycle(initialValue = persistentListOf())
        else -> mentionViewModel.mentionsUiStates.collectAsStateWithLifecycle(initialValue = persistentListOf())
    }

    ChatScreen(
        messages = messages,
        fontSize = displaySettings.fontSize,
        callbacks =
            ChatScreenCallbacks(
                onUserClick = { userId, userName, displayName, channel, badges, _ ->
                    openUserCard(userId, userName, displayName, channel, badges)
                },
                onMessageLongClick = openMessageOptions,
                onEmoteClick = { emoteInfoViewModel.show(it) },
                onWhisperReply = if (isWhisperTab) onWhisperReply else null,
                onMessageTap = if (isWhisperTab || canTapMentions) onMessageTap else null,
            ),
        animateGifs = displaySettings.animateGifs,
        showChannelPrefix = !isWhisperTab,
        modifier = modifier,
        contentPadding = contentPadding,
        scrollModifier = scrollModifier,
        containerColor = containerColor,
        onScrollToBottom = onScrollToBottom,
    )
}
