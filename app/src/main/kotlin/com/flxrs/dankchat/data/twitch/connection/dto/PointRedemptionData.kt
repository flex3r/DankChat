package com.flxrs.dankchat.data.twitch.connection.dto

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PointRedemption(
    val redemption: PointRedemptionData,
    val timestamp: String,
)

@Serializable
data class PointRedemptionData(
    val id: String,
    val user: PointRedemptionUser,
    val reward: PointRedemptionReward,
)

@Serializable
data class PointRedemptionUser(
    val id: UserId,
    @SerialName("login") val name: UserName,
    @SerialName("display_name") val displayName: DisplayName,
)

@Serializable
data class PointRedemptionReward(
    val id: String,
    val title: String,
    val cost: Int,
    @SerialName("is_user_input_required") val requiresUserInput: Boolean,
    @SerialName("image") val images: PointRedemptionImages?,
    @SerialName("default_image") val defaultImages: PointRedemptionImages,
)

@Serializable
data class PointRedemptionImages(
    @SerialName("url_1x") val imageSmall: String,
    @SerialName("url_2x") val imageMedium: String,
    @SerialName("url_4x") val imageLarge: String,
)