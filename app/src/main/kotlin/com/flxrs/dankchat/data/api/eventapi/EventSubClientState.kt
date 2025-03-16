package com.flxrs.dankchat.data.api.eventapi

sealed interface EventSubClientState {
    data object Disconnected : EventSubClientState
    data object Failed : EventSubClientState
    data object Connecting : EventSubClientState
    data class Connected(val sessionId: String) : EventSubClientState
}
