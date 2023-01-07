package com.flxrs.dankchat.data.twitch.pubsub.dto.whisper

import androidx.annotation.Keep
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class WhisperDataTags(
    @SerialName("login") val name: UserName,
    @SerialName("display_name") val displayName: DisplayName,
    val color: String,
    val emotes: List<WhisperDataEmote>,
    val badges: List<WhisperDataBadge>,
)
