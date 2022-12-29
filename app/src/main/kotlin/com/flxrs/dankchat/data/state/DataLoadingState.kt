package com.flxrs.dankchat.data.state

import com.flxrs.dankchat.data.repo.chat.ChatLoadingFailure
import com.flxrs.dankchat.data.repo.data.DataLoadingFailure

sealed class DataLoadingState {
    object None : DataLoadingState()
    object Finished : DataLoadingState()
    object Reloaded : DataLoadingState()
    object Loading : DataLoadingState()
    data class Failed(
        val errorMessage: String,
        val errorCount: Int,
        val dataFailures: Set<DataLoadingFailure>,
        val chatFailures: Set<ChatLoadingFailure>
    ) : DataLoadingState()
}