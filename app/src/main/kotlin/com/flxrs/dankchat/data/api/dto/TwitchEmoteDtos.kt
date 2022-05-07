package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class TwitchEmoteDto(
    @SerialName(value = "code") val name: String,
    @SerialName(value = "id") val id: String,
    @SerialName(value = "type") val type: String?,
    @SerialName(value = "assetType") val assetType: String?,
)

@Keep
@Serializable
data class DankChatEmoteSetDto(
    @SerialName(value = "set_id") val id: String,
    @SerialName(value = "channel_name") val channelName: String,
    @SerialName(value = "channel_id") val channelId: String,
    @SerialName(value = "tier") val tier: Int,
    @SerialName(value = "emotes") val emotes: List<TwitchEmoteDto>?
)

@Keep
data class HelixEmoteSetsDto(
    @field:Json(name = "data") val sets: List<HelixEmoteSetDto>
)

@Keep
data class HelixEmoteSetDto(
    @field:Json(name = "emote_set_id") val setId: String,
    @field:Json(name = "owner_id") val channelId: String,
)