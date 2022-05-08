package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class BTTVEmoteDto(
    @SerialName(value = "id") val id: String,
    @SerialName(value = "code") val code: String,
)

@Keep
@Serializable
data class BTTVGlobalEmotesDto(
    @SerialName(value = "id") val id: String,
    @SerialName(value = "code") val code: String,
)

@Keep
@Serializable
data class BTTVChannelDto(
    @SerialName(value = "id") val id: String,
    @SerialName(value = "bots") val bots: List<String>,
    @SerialName(value = "channelEmotes") val emotes: List<BTTVEmoteDto>,
    @SerialName(value = "sharedEmotes") val sharedEmotes: List<BTTVEmoteDto>
)