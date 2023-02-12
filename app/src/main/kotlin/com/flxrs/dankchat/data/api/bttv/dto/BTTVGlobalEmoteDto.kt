package com.flxrs.dankchat.data.api.bttv.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class BTTVGlobalEmoteDto(
    @SerialName(value = "id") val id: String,
    @SerialName(value = "code") val code: String,
)