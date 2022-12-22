package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SevenTVEmoteDto(
    @SerialName(value = "name") val name: String,
    @SerialName(value = "urls") val urls: List<List<String>>,
    @SerialName(value = "id") val id: String,
    @SerialName(value = "mime") val mime: String,
    @SerialName(value = "visibility_simple") val visibility: List<SevenTVEmoteVisibility?>
)

