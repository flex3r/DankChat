package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SevenTVEmoteSetDto(
    val emotes: List<SevenTVEmoteDto>?
)
