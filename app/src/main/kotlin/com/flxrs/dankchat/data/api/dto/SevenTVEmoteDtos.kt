package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class SevenTVEmoteDto(
    @field:Json(name = "name") val name: String,
    @field:Json(name = "urls") val urls: List<List<String>>,
    @field:Json(name = "id") val id: String,
    @field:Json(name = "mime") val mime: String,
    @field:Json(name = "visibility_simple") val visibility: List<SevenTVEmoteVisibility?>
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