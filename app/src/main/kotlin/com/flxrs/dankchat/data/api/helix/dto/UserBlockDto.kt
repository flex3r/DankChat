package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class UserBlockDto(
    @SerialName(value = "user_id") val id: UserId
)