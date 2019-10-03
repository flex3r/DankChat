package com.flxrs.dankchat.service.api.model

import com.squareup.moshi.Json

sealed class BadgeEntities {

    data class Badge(
        @field:Json(name = "image_url_1x") val imageUrlLow: String,
        @field:Json(name = "image_url_2x") val imageUrlMedium: String,
        @field:Json(name = "image_url_4x") val imageUrlHigh: String
//        @field:Json(name = "description") val description: String,
//        @field:Json(name = "title") val title: String,
//        @field:Json(name = "click_action") val click_action: String,
//        @field:Json(name = "click_url") val click_url: String,
//        @field:Json(name = "last_updated") val lastUpdated: String?
    )

    data class BadgeSet(@field:Json(name = "versions") val versions: Map<String, Badge>)

    data class Result(@field:Json(name = "badge_sets") val sets: Map<String, BadgeSet>)
}