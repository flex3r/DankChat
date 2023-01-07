package com.flxrs.dankchat.data.twitch.connection.dto.redemption

import androidx.annotation.Keep
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class PointRedemptionUser(
    val id: UserId,
    @SerialName("login") val name: UserName,
    @SerialName("display_name") val displayName: DisplayName,
)
