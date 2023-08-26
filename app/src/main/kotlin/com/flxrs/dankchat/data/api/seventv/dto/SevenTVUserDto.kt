package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SevenTVUserDto(
    val id: String,
    val user: SevenTVUserDataDto,
    @SerialName("emote_set") val emoteSet: SevenTVEmoteSetDto?
)
