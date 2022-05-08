package com.flxrs.dankchat.data.api.dto

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

@Keep
enum class SevenTVEmoteVisibility {
    PRIVATE,
    GLOBAL,
    UNLISTED,
    OVERRIDE_FFZ,
    OVERRIDE_BTTV,
    OVERRIDE_TWITCH_SUBSCRIBER,
    OVERRIDE_TWITCH_GLOBAL,
    ZERO_WIDTH
}