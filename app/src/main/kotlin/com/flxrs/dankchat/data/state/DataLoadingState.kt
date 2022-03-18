package com.flxrs.dankchat.data.state

sealed class DataLoadingState {

    object None : DataLoadingState()
    object Finished : DataLoadingState()
    object Reloaded : DataLoadingState()
    data class Loading(val parameters: Parameters) : DataLoadingState()
    data class Failed(val errorMessage: String, val parameters: Parameters) : DataLoadingState()

    data class Parameters(
        val channels: List<String> = emptyList(),
        val isReloadEmotes: Boolean = false,
        val isUserChange: Boolean = false,
        val loadTwitchData: Boolean = false,
        val loadSupibot: Boolean = false
    )
}