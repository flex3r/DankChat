package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class MarkerRequestDto(
    @SerialName("user_id") val userId: UserId,
    val description: String?
)
