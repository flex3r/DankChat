package com.flxrs.dankchat.data.api.eventapi.dto.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("revocation")
data class RevocationMessageDto(
    override val metadata: EventSubSubscriptionMetadataDto,
    override val payload: RevocationMessagePayload,
) : EventSubMessageDto

@Serializable
data class RevocationMessagePayload(
    val subscription: SubscriptionPayloadDto,
) : EventSubPayloadDto
