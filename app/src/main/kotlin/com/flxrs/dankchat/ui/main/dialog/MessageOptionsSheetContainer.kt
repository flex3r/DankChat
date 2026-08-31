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
import com.flxrs.dankchat.ui.main.MainEvent
import com.flxrs.dankchat.ui.main.MainEventBus
import com.flxrs.dankchat.ui.main.input.ChatInputViewModel
import com.flxrs.dankchat.ui.main.sheet.SheetNavigationViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MessageOptionsSheetContainer(onJumpToMessage: (messageId: String, channel: UserName) -> Unit) {
    val messageOptionsViewModel: MessageOptionsViewModel = koinViewModel()
    val chatInputViewModel: ChatInputViewModel = koinViewModel()
    val sheetNavigationViewModel: SheetNavigationViewModel = koinViewModel()
    val mainEventBus: MainEventBus = koinInject()

    val uiState by messageOptionsViewModel.state.collectAsStateWithLifecycle()
    val currentState = uiState ?: return

    val params = currentState.params
    val found = currentState.optionsState as? MessageOptionsState.Found ?: return
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    when (found) {
        is MessageOptionsState.Found.RegularMessage -> {
            MessageOptionsDialog(
                channel = params.channel?.value,
                canModerate = found.canModerate,
                canReply = found.canReply,
                canCopy = params.canCopy,
                canJump = params.canJump,
                hasReplyThread = found.hasReplyThread,
                urls = found.urls,
                onJumpToMessage = {
                    params.channel?.let { channel ->
                        onJumpToMessage(params.messageId, channel)
                    }
                },
                onReply = {
                    chatInputViewModel.setReplying(true, found.messageId, found.replyName, found.originalMessage)
                },
                onReplyToOriginal = {
                    chatInputViewModel.setReplying(true, found.rootThreadId, found.rootThreadName ?: found.replyName, found.rootThreadMessage.orEmpty())
                },
                onViewThread = {
                    sheetNavigationViewModel.openReplies(found.rootThreadId, found.replyName)
                },
                onCopy = {
                    scope.launch {
                        clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("message", found.originalMessage)))
                        mainEventBus.emitEvent(MainEvent.MessageCopied(found.originalMessage))
                    }
                },
                onCopyFullMessage = {
                    scope.launch {
                        clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("full message", params.fullMessage)))
                        mainEventBus.emitEvent(MainEvent.MessageCopied(params.fullMessage))
                    }
                },
                onCopyMessageId = {
                    scope.launch {
                        clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("message id", found.messageId)))
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

        is MessageOptionsState.Found.AutomodMessage -> {
            AutomodMessageOptionsDialog(
                canModerate = found.canModerate,
                startWithBan = params.startWithBan,
                onCopy = {
                    scope.launch {
                        clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("message", found.originalMessage)))
                        mainEventBus.emitEvent(MainEvent.MessageCopied(found.originalMessage))
                    }
                },
                onBan = messageOptionsViewModel::banUser,
                onUnban = messageOptionsViewModel::unbanUser,
                onWarn = messageOptionsViewModel::warnUser,
                onDismiss = messageOptionsViewModel::dismiss,
            )
        }
    }
}
