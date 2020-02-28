package com.flxrs.dankchat.service.api.model

import androidx.annotation.Keep
import com.squareup.moshi.Json

sealed class BadgeEntities {

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
}