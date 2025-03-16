package com.flxrs.dankchat.data.api.eventapi.dto.messages

import com.flxrs.dankchat.data.api.eventapi.dto.EventSubSubscriptionType
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("message_type")
sealed interface EventSubMessageDto {
    val metadata: EventSubMetadataDto
    val payload: EventSubPayloadDto
}

@Serializable
sealed interface EventSubMetadataDto {
    val messageId: String
    val messageType: EventSubMessageType
    val messageTimestamp: Instant
}

@Serializable
data class EventSubMessageMetadataDto(
    @SerialName("message_id")
    override val messageId: String,
    @SerialName("message_type")
    override val messageType: EventSubMessageType = EventSubMessageType.Unknown,
    @SerialName("message_timestamp")
    override val messageTimestamp: Instant,
) : EventSubMetadataDto

@Serializable
data class EventSubSubscriptionMetadataDto(
    @SerialName("message_id")
    override val messageId: String,
    @SerialName("message_type")
    override val messageType: EventSubMessageType = EventSubMessageType.Unknown,
    @SerialName("message_timestamp")
    override val messageTimestamp: Instant,
    @SerialName("subscription_type")
    val subscriptionType: EventSubSubscriptionType,
    @SerialName("subscription_version")
    val subscriptionVersion: String,
) : EventSubMetadataDto

@Serializable
enum class EventSubMessageType {
    @SerialName("session_welcome")
    Welcome,
    @SerialName("session_keepalive")
    KeepAlive,
    @SerialName("notification")
    Notification,
    @SerialName("revocation")
    Revocation,
    @SerialName("reconnect")
    Reconnect,
    Unknown,
}

interface EventSubPayloadDto

@Serializable
data object EmptyPayload : EventSubPayloadDto
