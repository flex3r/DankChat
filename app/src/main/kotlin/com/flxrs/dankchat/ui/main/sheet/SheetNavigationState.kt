package com.flxrs.dankchat.ui.main.sheet

import androidx.compose.runtime.Immutable
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.chat.history.HistoryChannel

// An openId identifies one opening, so its state is kept when the sheet is recomposed elsewhere and reset when it opens again
@Immutable
sealed interface FullScreenSheetState {
    data object Closed : FullScreenSheetState

    data class Replies(
        val replyMessageId: String,
        val replyName: UserName,
        val openId: Long,
    ) : FullScreenSheetState

    data class Mention(
        val openId: Long,
    ) : FullScreenSheetState

    data class Whisper(
        val openId: Long,
    ) : FullScreenSheetState

    data class History(
        val channel: HistoryChannel,
        val initialFilter: String,
        val openId: Long,
    ) : FullScreenSheetState
}

@Immutable
sealed interface InputSheetState {
    data object Closed : InputSheetState

    data object EmoteMenu : InputSheetState

    data object DebugInfo : InputSheetState
}

@Immutable
data class SheetNavigationState(
    val fullScreenSheet: FullScreenSheetState = FullScreenSheetState.Closed,
    val inputSheet: InputSheetState = InputSheetState.Closed,
)
