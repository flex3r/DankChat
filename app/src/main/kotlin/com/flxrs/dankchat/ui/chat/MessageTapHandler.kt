package com.flxrs.dankchat.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.chat.MessageTapAction
import com.flxrs.dankchat.ui.chat.message.rememberMessageCopyActions
import com.flxrs.dankchat.ui.chat.user.UserPopupStateParams
import com.flxrs.dankchat.ui.chat.user.UserPopupViewModel
import com.flxrs.dankchat.ui.main.input.ChatInputViewModel
import com.flxrs.dankchat.ui.main.sheet.SheetNavigationViewModel
import kotlinx.collections.immutable.ImmutableList
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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

/**
 * Resolves the configured message tap action into a handler, or null when tapping a message does nothing.
 * Screens provide their own reply and message options behaviour, the remaining actions are shared.
 */
@Composable
internal fun rememberMessageTapHandler(
    reply: (MessageTapContext) -> Unit,
    openMessageOptions: (MessageTapContext) -> Unit,
    allowMention: Boolean = true,
): ((MessageTapContext) -> Unit)? {
    val chatSettingsDataStore: ChatSettingsDataStore = koinInject()
    val preferenceStore: DankChatPreferenceStore = koinInject()
    val userPopupViewModel: UserPopupViewModel = koinViewModel()
    val chatInputViewModel: ChatInputViewModel = koinViewModel()
    val sheetNavigationViewModel: SheetNavigationViewModel = koinViewModel()
    val copyActions = rememberMessageCopyActions()
    val action by chatSettingsDataStore.messageTapAction.collectAsStateWithLifecycle(initialValue = chatSettingsDataStore.current().messageTapAction)
    val isLoggedIn = preferenceStore.isLoggedIn
    val currentReply by rememberUpdatedState(reply)
    val currentOpenMessageOptions by rememberUpdatedState(openMessageOptions)

    return remember(action, isLoggedIn, allowMention, userPopupViewModel, chatInputViewModel, sheetNavigationViewModel, copyActions) {
        when {
            action == MessageTapAction.Mention && !allowMention -> {
                null
            }

            else -> {
                messageTapHandler(
                    action = action,
                    isLoggedIn = isLoggedIn,
                    operations =
                        MessageTapOperations(
                            reply = { currentReply(it) },
                            mention = { chatInputViewModel.mentionUser(it.userName, it.displayName) },
                            whisper = {
                                sheetNavigationViewModel.openWhispers()
                                chatInputViewModel.setWhisperTarget(it.userName)
                            },
                            openUserCard = {
                                userPopupViewModel.show(
                                    UserPopupStateParams(
                                        targetUserId = it.userId,
                                        targetUserName = it.userName,
                                        targetDisplayName = it.displayName,
                                        channel = it.channel,
                                        badges = it.badges.map { badge -> badge.badge },
                                    ),
                                )
                            },
                            openMessageOptions = { currentOpenMessageOptions(it) },
                            copyMessage = copyActions.copyMessage,
                            copyFullMessage = copyActions.copyFullMessage,
                        ),
                )
            }
        }
    }
}
