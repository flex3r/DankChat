package com.flxrs.dankchat.data.twitch.pubsub.dto.redemption

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class PointRedemptionData(
    val id: String,
    val user: PointRedemptionUser,
    val reward: PointRedemptionReward,
)
