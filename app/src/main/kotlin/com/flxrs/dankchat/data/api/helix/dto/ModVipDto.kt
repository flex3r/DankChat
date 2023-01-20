package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ModVipDto(
    @SerialName("user_id") val userId: UserId,
    @SerialName("user_login") val userLogin: UserName,
    @SerialName("user_name") val userName: DisplayName,
)
