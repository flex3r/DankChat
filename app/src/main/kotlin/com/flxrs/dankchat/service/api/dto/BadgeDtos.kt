package com.flxrs.dankchat.service.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

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
data class DankChatBadgeDto(@field:Json(name = "type") val type: String, @field:Json(name = "url") val url: String, @field:Json(name = "users") val users: List<String>)