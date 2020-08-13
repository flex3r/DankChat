package com.flxrs.dankchat.service.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

sealed class BadgeDtos {

    @Keep
    data class Badge(
        @field:Json(name = "image_url_1x") val imageUrlLow: String,
        @field:Json(name = "image_url_2x") val imageUrlMedium: String,
        @field:Json(name = "image_url_4x") val imageUrlHigh: String
    )

    @Keep
    data class BadgeSet(@field:Json(name = "versions") val versions: Map<String, Badge>)

    @Keep
    data class Result(@field:Json(name = "badge_sets") val sets: Map<String, BadgeSet>)

    @Keep
    data class DankChatBadge(@field:Json(name = "type") val type: String, @field:Json(name = "url") val url: String, @field:Json(name = "users") val users: List<String>)
}