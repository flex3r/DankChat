package com.flxrs.dankchat.data.api.ffz.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class FFZEmoteDto(
    @SerialName(value = "urls") val urls: Map<String, String?>,
    @SerialName(value = "name") val name: String,
    @SerialName(value = "id") val id: Int
)