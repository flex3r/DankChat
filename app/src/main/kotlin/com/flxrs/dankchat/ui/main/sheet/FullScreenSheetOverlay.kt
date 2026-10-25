package com.flxrs.dankchat.ui.main.sheet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.chat.MessageTapContext
import com.flxrs.dankchat.ui.chat.history.HistoryChannel
import com.flxrs.dankchat.ui.chat.history.MessageHistoryViewModel
import com.flxrs.dankchat.ui.chat.mention.MentionViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Suppress("ViewModelForwarding")
@Composable
fun FullScreenSheetOverlay(
    sheetState: FullScreenSheetState,
    mentionViewModel: MentionViewModel,
    onDismiss: () -> Unit,
    onDismissReplies: () -> Unit,
    modifier: Modifier = Modifier,
    onWhisperReply: (UserName) -> Unit = {},
    onReplyToMessage: (MessageTapContext) -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
) {
    // Open states share one content key, so switching sheets updates in place and a closing sheet keeps its content while it exits
    AnimatedContent(
        targetState = sheetState,
        transitionSpec = {
            (fadeIn() + expandVertically(expandFrom = Alignment.Top)) togetherWith (fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top))
        },
        contentKey = { it !is FullScreenSheetState.Closed },
        label = "FullScreenSheet",
        modifier = modifier.fillMaxSize(),
    ) { state ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (state) {
                is FullScreenSheetState.Closed -> Unit

                is FullScreenSheetState.Mention -> {
                    MentionSheet(
                        mentionViewModel = mentionViewModel,
                        initialisWhisperTab = false,
                        onDismiss = onDismiss,
                        onWhisperReply = onWhisperReply,
                        onReplyToMessage = onReplyToMessage,
                        bottomContentPadding = bottomContentPadding,
                    )
                }

                is FullScreenSheetState.Whisper -> {
                    MentionSheet(
                        mentionViewModel = mentionViewModel,
                        initialisWhisperTab = true,
                        onDismiss = onDismiss,
                        onWhisperReply = onWhisperReply,
                        onReplyToMessage = onReplyToMessage,
                        bottomContentPadding = bottomContentPadding,
                    )
                }

                is FullScreenSheetState.Replies -> {
                    RepliesSheet(
                        rootMessageId = state.replyMessageId,
                        onDismiss = onDismissReplies,
                        bottomContentPadding = bottomContentPadding,
                    )
                }

                is FullScreenSheetState.History -> {
                    HistorySheetContent(
                        historyChannel = state.channel,
                        initialFilter = state.initialFilter,
                        openId = state.openId,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySheetContent(
    historyChannel: HistoryChannel,
    initialFilter: String,
    openId: Long,
    onDismiss: () -> Unit,
) {
    val viewModel: MessageHistoryViewModel =
        koinViewModel(
            parameters = { parametersOf(historyChannel) },
        )
    SideEffect(historyChannel) {
        viewModel.selectChannel(historyChannel)
    }
    MessageHistorySheet(
        viewModel = viewModel,
        initialFilter = initialFilter,
        openId = openId,
        onDismiss = onDismiss,
    )
}
