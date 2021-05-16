package com.flxrs.dankchat.service.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class TwitchEmoteDto(
    @field:Json(name = "code") val name: String,
    @field:Json(name = "id") val id: String
)

@Keep
data class TwitchEmotesDto(@field:Json(name = "emoticon_sets") val sets: Map<String, List<TwitchEmoteDto>>)

@Keep
data class TwitchEmoteSetDto(
    @field:Json(name = "set_id") val id: String,
    @field:Json(name = "channel_name") val channelName: String,
    @field:Json(name = "channel_id") val channelId: String,
    @field:Json(name = "tier") val tier: Int,
    @field:Json(name = "emotes") val emotes: List<TwitchEmoteDto>?
)