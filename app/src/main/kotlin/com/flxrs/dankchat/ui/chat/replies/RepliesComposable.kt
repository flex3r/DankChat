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
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.chat.UserLongClickBehavior
import com.flxrs.dankchat.ui.chat.ChatScreen
import com.flxrs.dankchat.ui.chat.ChatScreenCallbacks
import com.flxrs.dankchat.ui.chat.emote.EmoteInfoViewModel
import com.flxrs.dankchat.ui.chat.message.MessageOptionsParams
import com.flxrs.dankchat.ui.chat.message.MessageOptionsViewModel
import com.flxrs.dankchat.ui.chat.user.UserPopupStateParams
import com.flxrs.dankchat.ui.chat.user.UserPopupViewModel
import com.flxrs.dankchat.ui.main.input.ChatInputViewModel
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
    val chatSettingsDataStore: ChatSettingsDataStore = koinInject()
    val displaySettings by repliesViewModel.chatDisplaySettings.collectAsStateWithLifecycle()
    val userLongClickBehavior by chatSettingsDataStore.userLongClickBehavior.collectAsStateWithLifecycle(initialValue = UserLongClickBehavior.MentionsUser)
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
                                userPopupViewModel.show(
                                    UserPopupStateParams(
                                        targetUserId = userId?.let { UserId(it) },
                                        targetUserName = UserName(userName),
                                        targetDisplayName = DisplayName(displayName),
                                        channel = channel?.let { UserName(it) },
                                        badges = badges.map { it.badge },
                                    ),
                                )
                            } else {
                                chatInputViewModel.mentionUser(UserName(userName), DisplayName(displayName))
                            }
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
