package com.flxrs.dankchat.service.state

sealed class DataLoadingState {

    object Finished : DataLoadingState()
    object Reloaded : DataLoadingState()
    data class Loading(val parameters: Parameters) : DataLoadingState()
    data class Failed(val t: Throwable, val parameters: Parameters) : DataLoadingState()

    data class Parameters(
        val oAuth: String = "",
        val id: String = "",
        val name: String = "",
        val channels: List<String> = emptyList(),
        val isReloadEmotes: Boolean = false,
        val loadTwitchData: Boolean = false,
        val loadHistory: Boolean = false,
        val loadSupibot: Boolean = false
    )
}