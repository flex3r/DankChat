package com.flxrs.dankchat.data.twitch.connection.dto.whisper

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class WhisperDataBadge(
    val id: String,
    val version: String,
)
