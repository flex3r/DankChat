package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SevenTVEmoteHostDto(
    val url: String,
    val files: List<SevenTVEmoteFileDto>,
)
