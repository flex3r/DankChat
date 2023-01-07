package com.flxrs.dankchat.data.twitch.pubsub.dto.redemption

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class PointRedemptionImages(
    @SerialName("url_1x") val imageSmall: String,
    @SerialName("url_2x") val imageMedium: String,
    @SerialName("url_4x") val imageLarge: String,
)
