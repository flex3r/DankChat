package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class CommercialDto(
    val length: Int,
    val message: String?,
    @SerialName("retry_after") val retryAfter: Int,
)
