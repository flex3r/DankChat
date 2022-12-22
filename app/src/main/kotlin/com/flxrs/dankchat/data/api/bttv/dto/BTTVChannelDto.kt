package com.flxrs.dankchat.data.api.bttv.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class BTTVChannelDto(
    @SerialName(value = "id") val id: String,
    @SerialName(value = "bots") val bots: List<String>,
    @SerialName(value = "channelEmotes") val emotes: List<BTTVEmoteDto>,
    @SerialName(value = "sharedEmotes") val sharedEmotes: List<BTTVEmoteDto>
)