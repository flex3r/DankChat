package com.flxrs.dankchat.data.api.eventapi.dto.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("session_keepalive")
data class KeepAliveMessageDto(
    override val metadata: EventSubMessageMetadataDto,
    override val payload: EmptyPayload,
) : EventSubMessageDto
