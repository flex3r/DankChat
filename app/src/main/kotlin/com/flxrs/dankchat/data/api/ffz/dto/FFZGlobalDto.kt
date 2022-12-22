package com.flxrs.dankchat.data.api.ffz.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class FFZGlobalDto(
    @SerialName(value = "default_sets") val defaultSets: List<String>,
    @SerialName(value = "sets") val sets: Map<String, FFZEmoteSetDto>
)