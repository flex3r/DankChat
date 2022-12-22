package com.flxrs.dankchat.data.api.ffz.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class FFZChannelDto(
    @SerialName(value = "room") val room: FFZRoomDto,
    @SerialName(value = "sets") val sets: Map<String, FFZEmoteSetDto>
)