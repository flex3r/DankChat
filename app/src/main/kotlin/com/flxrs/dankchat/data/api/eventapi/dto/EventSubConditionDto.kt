package com.flxrs.dankchat.data.api.eventapi.dto

import com.flxrs.dankchat.data.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface EventSubConditionDto

@Serializable
data class EventSubUserConditionDto(
    @SerialName("user_id")
    val userId: UserId,
) : EventSubConditionDto

@Serializable
data class EventSubModeratorConditionDto(
    @SerialName("broadcaster_user_id")
    val broadcasterUserId: UserId,
    @SerialName("moderator_user_id")
    val moderatorUserId: UserId,
) : EventSubConditionDto
