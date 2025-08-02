package com.flxrs.dankchat.data.api.eventapi.dto.messages

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("session_welcome")
data class WelcomeMessageDto(
    override val metadata: EventSubMessageMetadataDto,
    override val payload: WelcomeMessagePayload,
) : EventSubMessageDto

@Serializable
data class WelcomeMessagePayload(
    val session: SessionPayloadDto,
) : EventSubPayloadDto

@Serializable
data class SessionPayloadDto(
    val id: String,
    val status: String,
    @SerialName("connected_at")
    val connectedAt: Instant,
    @SerialName("keepalive_timeout_seconds")
    val keepAliveSeconds: Int?,
    @SerialName("reconnect_url")
    val reconnectUrl: String? = null,
)
