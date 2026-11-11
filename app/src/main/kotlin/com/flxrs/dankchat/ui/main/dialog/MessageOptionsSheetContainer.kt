package com.flxrs.dankchat.ui.main.dialog

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.chat.message.MessageOptionsState
import com.flxrs.dankchat.ui.chat.message.MessageOptionsViewModel
import com.flxrs.dankchat.ui.chat.message.MessageReplyAction
import com.flxrs.dankchat.ui.chat.message.rememberMessageCopyActions
import com.flxrs.dankchat.ui.main.MainEvent
import com.flxrs.dankchat.ui.main.MainEventBus
import com.flxrs.dankchat.ui.main.input.ChatInputViewModel
import com.flxrs.dankchat.ui.main.sheet.SheetNavigationViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MessageOptionsSheetContainer(
    onJumpToMessage: (messageId: String, channel: UserName) -> Unit,
    onReplyToMessage: (messageId: String, channel: UserName, userName: UserName, message: String) -> Unit,
) {
    val messageOptionsViewModel: MessageOptionsViewModel = koinViewModel()
    val chatInputViewModel: ChatInputViewModel = koinViewModel()
    val sheetNavigationViewModel: SheetNavigationViewModel = koinViewModel()
    val mainEventBus: MainEventBus = koinInject()

    val uiState by messageOptionsViewModel.state.collectAsStateWithLifecycle()
    val currentState = uiState ?: return

    val params = currentState.params
    val optionsState = currentState.optionsState
    val clipboardManager = LocalClipboard.current
    val messageCopyActions = rememberMessageCopyActions()
    val scope = rememberCoroutineScope()

    when (optionsState) {
        is MessageOptionsState.RegularMessage -> {
            MessageOptionsDialog(
                channel = params.channel?.value,
                canModerate = optionsState.canModerate,
                canReply = optionsState.replyAction != null,
                canCopy = params.canCopy,
                canJump = params.canJump,
                hasReplyThread = optionsState.hasReplyThread,
                urls = optionsState.urls,
                onJumpToMessage = {
                    params.channel?.let { channel ->
                        onJumpToMessage(params.messageId, channel)
                    }
                },
                onReply = {
                    when (val action = optionsState.replyAction) {
                        MessageReplyAction.Channel -> chatInputViewModel.startReply(optionsState.messageId, optionsState.replyName, optionsState.originalMessage)

                        MessageReplyAction.JumpToChannel -> params.channel?.let { channel ->
                            onReplyToMessage(optionsState.messageId, channel, optionsState.replyName, optionsState.originalMessage)
                        }

                        is MessageReplyAction.Whisper -> {
                            sheetNavigationViewModel.openWhispers()
                            chatInputViewModel.setWhisperTarget(action.target)
                        }

                        null -> Unit
                    }
                },
                onReplyToOriginal = {
                    chatInputViewModel.startReply(optionsState.rootThreadId, optionsState.rootThreadName ?: optionsState.replyName, optionsState.rootThreadMessage.orEmpty())
                },
                onViewThread = {
                    sheetNavigationViewModel.openReplies(optionsState.rootThreadId, optionsState.replyName)
                },
                onCopy = {
                    messageCopyActions.copyMessage(optionsState.originalMessage)
                },
                onCopyFullMessage = {
                    messageCopyActions.copyFullMessage(params.fullMessage)
                },
                onCopyMessageId = {
                    scope.launch {
                        clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("message id", optionsState.messageId)))
                        mainEventBus.emitEvent(MainEvent.MessageIdCopied)
                    }
                },
                onCopyUrl = { url ->
                    scope.launch {
                        clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("url", url)))
                        mainEventBus.emitEvent(MainEvent.LinkCopied(url))
                    }
                },
                onDelete = messageOptionsViewModel::deleteMessage,
                onTimeout = messageOptionsViewModel::timeoutUser,
                onBan = messageOptionsViewModel::banUser,
                onUnban = messageOptionsViewModel::unbanUser,
                onWarn = messageOptionsViewModel::warnUser,
                onPinMessage = messageOptionsViewModel::pinMessage,
                onDismiss = messageOptionsViewModel::dismiss,
            )
        }

        is MessageOptionsState.AutomodMessage -> {
            AutomodMessageOptionsDialog(
                canModerate = optionsState.canModerate,
                startWithBan = params.startWithBan,
                onCopy = {
                    messageCopyActions.copyMessage(optionsState.originalMessage)
                },
                onBan = messageOptionsViewModel::banUser,
                onUnban = messageOptionsViewModel::unbanUser,
                onWarn = messageOptionsViewModel::warnUser,
                onDismiss = messageOptionsViewModel::dismiss,
            )
        }
    }
}
