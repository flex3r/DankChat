package com.flxrs.dankchat.data.api.ffz.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class FFZEmoteSetDto(@SerialName(value = "emoticons") val emotes: List<FFZEmoteDto>)