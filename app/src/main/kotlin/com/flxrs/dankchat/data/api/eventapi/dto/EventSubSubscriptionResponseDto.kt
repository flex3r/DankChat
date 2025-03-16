package com.flxrs.dankchat.data.api.eventapi.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventSubSubscriptionResponseListDto(
    val data: List<EventSubSubscriptionResponseDto>,
    val total: Int,
    @SerialName("total_cost")
    val totalCost: Int,
    @SerialName("max_total_cost")
    val maxTotalCost: Int,
)

@Serializable
data class EventSubSubscriptionResponseDto(
    val id: String,
    val status: EventSubSubscriptionStatus = EventSubSubscriptionStatus.Unknown,
    val type: EventSubSubscriptionType = EventSubSubscriptionType.Unknown,
    val version: String,
    @SerialName("created_at")
    val createdAt: Instant,
    val transport: EventSubTransportDto,
    @SerialName("cost")
    val cost: Int,
)

@Serializable
enum class EventSubSubscriptionStatus {
    @SerialName("enabled")
    Enabled,
    @SerialName("authorization_revoked")
    AuthorizationRevoked,
    @SerialName("user_removed")
    UserRemoved,
    @SerialName("version_removed")
    VersionRemoved,
    Unknown,
}
