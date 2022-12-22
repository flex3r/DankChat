package com.flxrs.dankchat.data.api.badges.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class TwitchBadgeDto(
    @SerialName(value = "image_url_1x") val imageUrlLow: String,
    @SerialName(value = "image_url_2x") val imageUrlMedium: String,
    @SerialName(value = "image_url_4x") val imageUrlHigh: String,
    @SerialName(value = "title") val title: String,
)