package com.flxrs.dankchat.ui.main.sheet

import androidx.compose.runtime.Immutable
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.chat.history.HistoryChannel

@Immutable
sealed interface FullScreenSheetState {
    data object Closed : FullScreenSheetState

    data class Replies(
        val replyMessageId: String,
        val replyName: UserName,
    ) : FullScreenSheetState

    data object Mention : FullScreenSheetState

    data object Whisper : FullScreenSheetState

    data class History(
        val channel: HistoryChannel = HistoryChannel.Global,
        val initialFilter: String = "",
        // Identifies one opening, so the initial filter is applied once and not again when the sheet is recomposed elsewhere
        val openId: Long = 0L,
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
