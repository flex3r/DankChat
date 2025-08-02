package com.flxrs.dankchat.data.api.eventapi.dto.messages

import com.flxrs.dankchat.data.api.eventapi.dto.EventSubSubscriptionStatus
import com.flxrs.dankchat.data.api.eventapi.dto.EventSubSubscriptionType
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.NotificationEventDto
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("notification")
data class NotificationMessageDto(
    override val metadata: EventSubSubscriptionMetadataDto,
    override val payload: NotificationMessagePayload
) : EventSubMessageDto

@Serializable
data class NotificationMessagePayload(
    val subscription: SubscriptionPayloadDto,
    val event: NotificationEventDto,
) : EventSubPayloadDto

@Serializable
data class SubscriptionPayloadDto(
    val id: String,
    val status: EventSubSubscriptionStatus = EventSubSubscriptionStatus.Unknown,
    val type: EventSubSubscriptionType = EventSubSubscriptionType.Unknown,
    val version: String,
    val cost: Int,
    @SerialName("created_at")
    val createdAt: Instant,
)
