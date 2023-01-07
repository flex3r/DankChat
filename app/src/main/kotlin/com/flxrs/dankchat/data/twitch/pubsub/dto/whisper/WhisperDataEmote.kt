package com.flxrs.dankchat.data.twitch.pubsub.dto.whisper

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class WhisperDataEmote(
    @SerialName("emote_id") val id: String,
    val start: Int,
    val end: Int,
)
