package com.flxrs.dankchat.ui.main.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.data.UserName
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
    bottomContentPadding: Dp = 0.dp,
) {
    val isVisible = sheetState !is FullScreenSheetState.Closed

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (sheetState) {
                is FullScreenSheetState.Closed -> Unit

                is FullScreenSheetState.Mention -> {
                    MentionSheet(
                        mentionViewModel = mentionViewModel,
                        initialisWhisperTab = false,
                        onDismiss = onDismiss,
                        onWhisperReply = onWhisperReply,
                        bottomContentPadding = bottomContentPadding,
                    )
                }

                is FullScreenSheetState.Whisper -> {
                    MentionSheet(
                        mentionViewModel = mentionViewModel,
                        initialisWhisperTab = true,
                        onDismiss = onDismiss,
                        onWhisperReply = onWhisperReply,
                        bottomContentPadding = bottomContentPadding,
                    )
                }

                is FullScreenSheetState.Replies -> {
                    RepliesSheet(
                        rootMessageId = sheetState.replyMessageId,
                        onDismiss = onDismissReplies,
                        bottomContentPadding = bottomContentPadding,
                    )
                }

                is FullScreenSheetState.History -> {
                    HistorySheetContent(
                        historyChannel = sheetState.channel,
                        initialFilter = sheetState.initialFilter,
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
        onDismiss = onDismiss,
    )
}
