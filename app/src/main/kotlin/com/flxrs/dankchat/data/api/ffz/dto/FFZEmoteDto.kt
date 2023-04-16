package com.flxrs.dankchat.data.api.ffz.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class FFZEmoteDto(
    val urls: Map<String, String?>,
    val animated: Map<String, String?>?,
    val name: String,
    val id: Int,
    val owner: FFZEmoteOwnerDto?,
)
