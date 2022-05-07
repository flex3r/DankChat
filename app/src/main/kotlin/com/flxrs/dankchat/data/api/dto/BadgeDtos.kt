package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
data class TwitchBadgeDto(
    @field:Json(name = "image_url_1x") val imageUrlLow: String,
    @field:Json(name = "image_url_2x") val imageUrlMedium: String,
    @field:Json(name = "image_url_4x") val imageUrlHigh: String
)

@Keep
data class TwitchBadgeSetDto(@field:Json(name = "versions") val versions: Map<String, TwitchBadgeDto>)

@Keep
data class TwitchBadgesDto(@field:Json(name = "badge_sets") val sets: Map<String, TwitchBadgeSetDto>)

@Keep
@Serializable
data class DankChatBadgeDto(
    @SerialName(value = "type") val type: String,
    @SerialName(value = "url") val url: String,
    @SerialName(value = "users") val users: List<String>
)

@Keep
data class HelixBadgesDto(@field:Json(name = "data") val sets: List<HelixBadgeSetDto>)

@Keep
data class HelixBadgeSetDto(
    @field:Json(name = "set_id") val setId: String,
    @field:Json(name = "versions") val versions: List<HelixBadgeDto>
)

@Keep
data class HelixBadgeDto(
    @field:Json(name = "id") val badgeId: String,
    @field:Json(name = "image_url_1x") val imageUrlLow: String,
    @field:Json(name = "image_url_2x") val imageUrlMedium: String,
    @field:Json(name = "image_url_4x") val imageUrlHigh: String
)