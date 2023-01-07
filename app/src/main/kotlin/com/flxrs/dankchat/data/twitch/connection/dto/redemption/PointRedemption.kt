package com.flxrs.dankchat.data.twitch.connection.dto.redemption

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class PointRedemption(
    val redemption: PointRedemptionData,
    val timestamp: String,
)
