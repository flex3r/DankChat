package com.flxrs.dankchat.data.twitch.pubsub.dto.redemption

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class PointRedemptionReward(
    val id: String,
    val title: String,
    val cost: Int,
    @SerialName("is_user_input_required") val requiresUserInput: Boolean,
    @SerialName("image") val images: PointRedemptionImages?,
    @SerialName("default_image") val defaultImages: PointRedemptionImages,
)
