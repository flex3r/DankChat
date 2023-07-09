package com.flxrs.dankchat.data.state

import com.flxrs.dankchat.data.repo.chat.ChatLoadingFailure
import com.flxrs.dankchat.data.repo.data.DataLoadingFailure

sealed interface DataLoadingState {
    data object None : DataLoadingState
    data object Finished : DataLoadingState
    data object Reloaded : DataLoadingState
    data object Loading : DataLoadingState
    data class Failed(
        val errorMessage: String,
        val errorCount: Int,
        val dataFailures: Set<DataLoadingFailure>,
        val chatFailures: Set<ChatLoadingFailure>
    ) : DataLoadingState
}
