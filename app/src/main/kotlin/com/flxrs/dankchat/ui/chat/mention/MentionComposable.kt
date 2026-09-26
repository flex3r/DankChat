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
import com.flxrs.dankchat.ui.chat.ChatScreen
import com.flxrs.dankchat.ui.chat.ChatScreenCallbacks
import com.flxrs.dankchat.ui.chat.emote.EmoteInfoViewModel
import com.flxrs.dankchat.ui.chat.message.MessageOptionsParams
import com.flxrs.dankchat.ui.chat.message.MessageOptionsViewModel
import com.flxrs.dankchat.ui.chat.user.UserPopupStateParams
import com.flxrs.dankchat.ui.chat.user.UserPopupViewModel
import kotlinx.collections.immutable.persistentListOf
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
    val displaySettings by mentionViewModel.chatDisplaySettings.collectAsStateWithLifecycle()
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
                    userPopupViewModel.show(
                        UserPopupStateParams(
                            targetUserId = userId?.let { UserId(it) },
                            targetUserName = UserName(userName),
                            targetDisplayName = DisplayName(displayName),
                            channel = channel?.let { UserName(it) },
                            badges = badges.map { it.badge },
                        ),
                    )
                },
                onMessageLongClick = { messageId, channel, fullMessage ->
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
                },
                onEmoteClick = { emoteInfoViewModel.show(it) },
                onWhisperReply = if (isWhisperTab) onWhisperReply else null,
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
