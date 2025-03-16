package com.flxrs.dankchat.data.api.eventapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventSubTransportDto(
    val method: EventSubMethod,
    @SerialName("session_id")
    val sessionId: String,
)

@Serializable
enum class EventSubMethod {
    @SerialName("websocket")
    Websocket,
}
