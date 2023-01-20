package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class MarkerDto(
    val id: String,
    val description: String?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("position_seconds") val positionSeconds: Int,
)
