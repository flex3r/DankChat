package com.flxrs.dankchat.data.api.eventapi.dto

import kotlinx.serialization.Serializable

@Serializable
data class EventSubSubscriptionRequestDto(
    val type: EventSubSubscriptionType,
    val version: String,
    val condition: EventSubConditionDto,
    val transport: EventSubTransportDto,
)
