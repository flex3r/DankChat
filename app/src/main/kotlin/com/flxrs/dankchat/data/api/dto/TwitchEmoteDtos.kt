package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class TwitchEmoteDto(
    @field:Json(name = "code") val name: String,
    @field:Json(name = "id") val id: String,
    @field:Json(name = "type") val type: String?,
    @field:Json(name = "assetType") val assetType: String?,
)

@Keep
data class TwitchEmotesDto(@field:Json(name = "emoticon_sets") val sets: Map<String, List<TwitchEmoteDto>>)

@Keep
data class DankChatEmoteSetDto(
    @field:Json(name = "set_id") val id: String,
    @field:Json(name = "channel_name") val channelName: String,
    @field:Json(name = "channel_id") val channelId: String,
    @field:Json(name = "tier") val tier: Int,
    @field:Json(name = "emotes") val emotes: List<TwitchEmoteDto>?
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