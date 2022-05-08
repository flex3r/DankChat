package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class TwitchBadgeDto(
    @SerialName(value = "image_url_1x") val imageUrlLow: String,
    @SerialName(value = "image_url_2x") val imageUrlMedium: String,
    @SerialName(value = "image_url_4x") val imageUrlHigh: String
)

@Keep
@Serializable
data class TwitchBadgeSetDto(@SerialName(value = "versions") val versions: Map<String, TwitchBadgeDto>)

@Keep
@Serializable
data class TwitchBadgesDto(@SerialName(value = "badge_sets") val sets: Map<String, TwitchBadgeSetDto>)

@Keep
@Serializable
data class DankChatBadgeDto(
    @SerialName(value = "type") val type: String,
    @SerialName(value = "url") val url: String,
    @SerialName(value = "users") val users: List<String>
)

@Keep
@Serializable
data class HelixBadgesDto(@SerialName(value = "data") val sets: List<HelixBadgeSetDto>)

@Keep
@Serializable
data class HelixBadgeSetDto(
    @SerialName(value = "set_id") val setId: String,
    @SerialName(value = "versions") val versions: List<HelixBadgeDto>
)

@Keep
@Serializable
data class HelixBadgeDto(
    @SerialName(value = "id") val badgeId: String,
    @SerialName(value = "image_url_1x") val imageUrlLow: String,
    @SerialName(value = "image_url_2x") val imageUrlMedium: String,
    @SerialName(value = "image_url_4x") val imageUrlHigh: String
)