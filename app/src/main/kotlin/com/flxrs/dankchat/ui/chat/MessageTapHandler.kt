package com.flxrs.dankchat.ui.chat

import androidx.compose.runtime.Immutable
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.preferences.chat.MessageTapAction
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class MessageTapContext(
    val messageId: String,
    val channel: UserName?,
    val userId: UserId?,
    val userName: UserName,
    val displayName: DisplayName,
    val badges: ImmutableList<BadgeUi>,
    val message: String,
    val fullMessage: String,
    val isWhisper: Boolean,
)

internal fun ChatMessageUiState.PrivMessageUi.toMessageTapContext() = MessageTapContext(
    messageId = id,
    channel = channel,
    userId = userId,
    userName = userName,
    displayName = displayName,
    badges = badges,
    message = message,
    fullMessage = fullMessage,
    isWhisper = false,
)

internal fun ChatMessageUiState.WhisperMessageUi.toMessageTapContext() = MessageTapContext(
    messageId = id,
    channel = null,
    userId = replyTargetUserId,
    userName = replyTargetName,
    displayName = replyTargetDisplayName,
    badges = replyTargetBadges,
    message = message,
    fullMessage = fullMessage,
    isWhisper = true,
)

internal data class MessageTapOperations(
    val reply: (MessageTapContext) -> Unit,
    val mention: (MessageTapContext) -> Unit,
    val whisper: (MessageTapContext) -> Unit,
    val openUserCard: (MessageTapContext) -> Unit,
    val openMessageOptions: (MessageTapContext) -> Unit,
    val copyMessage: (String) -> Unit,
    val copyFullMessage: (String) -> Unit,
)

internal fun messageTapHandler(
    action: MessageTapAction,
    isLoggedIn: Boolean,
    operations: MessageTapOperations,
): ((MessageTapContext) -> Unit)? {
    val requiresLogin =
        action == MessageTapAction.Reply ||
            action == MessageTapAction.Mention ||
            action == MessageTapAction.Whisper
    if (action == MessageTapAction.DoNothing || (requiresLogin && !isLoggedIn)) {
        return null
    }

    return { message ->
        when (action) {
            MessageTapAction.DoNothing -> Unit
            MessageTapAction.Reply -> operations.reply(message)
            MessageTapAction.Mention -> operations.mention(message)
            MessageTapAction.Whisper -> operations.whisper(message)
            MessageTapAction.OpenUserCard -> operations.openUserCard(message)
            MessageTapAction.OpenMessageOptions -> operations.openMessageOptions(message)
            MessageTapAction.CopyMessage -> operations.copyMessage(message.message)
            MessageTapAction.CopyFullMessage -> operations.copyFullMessage(message.fullMessage)
        }
    }
}
