package com.flxrs.dankchat.service.state

sealed class DataLoadingState {

    object Loading : DataLoadingState()
    object Finished : DataLoadingState()
    object Reloaded : DataLoadingState()
    data class Failed(val t: Throwable) : DataLoadingState()
}