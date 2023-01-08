package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SevenTVEmoteFileDto(
    val name: String,
    val format: String,
    @SerialName("static_name") val staticName: String,
)
