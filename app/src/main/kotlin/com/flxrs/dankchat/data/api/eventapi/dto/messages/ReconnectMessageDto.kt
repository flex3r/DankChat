package com.flxrs.dankchat.data.api.eventapi.dto.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("session_reconnect")
data class ReconnectMessageDto(
    override val metadata: EventSubMessageMetadataDto,
    override val payload: ReconnectMessagePayload,
) : EventSubMessageDto

@Serializable
data class ReconnectMessagePayload(
    val session: SessionPayloadDto,
) : EventSubPayloadDto
