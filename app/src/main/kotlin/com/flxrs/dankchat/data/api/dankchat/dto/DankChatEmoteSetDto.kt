package com.flxrs.dankchat.data.api.dankchat.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class DankChatEmoteSetDto(
    @SerialName(value = "set_id") val id: String,
    @SerialName(value = "channel_name") val channelName: String,
    @SerialName(value = "channel_id") val channelId: String,
    @SerialName(value = "tier") val tier: Int,
    @SerialName(value = "emotes") val emotes: List<DankChatEmoteDto>?
)