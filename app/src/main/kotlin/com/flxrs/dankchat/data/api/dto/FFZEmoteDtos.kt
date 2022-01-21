package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class FFZEmoteDto(
    @field:Json(name = "urls") val urls: Map<String, String?>,
    @field:Json(name = "name") val name: String,
    @field:Json(name = "id") val id: Int
)

@Keep
data class FFZEmoteSetDto(
    @field:Json(name = "emoticons") val emotes: List<FFZEmoteDto>
)

@Keep
data class FFZRoomDto(
    @field:Json(name = "mod_urls") val modBadgeUrls: Map<String, String?>?,
    @field:Json(name = "vip_badge") val vipBadgeUrls: Map<String, String?>?,
)

@Keep
data class FFZChannelDto(
    @field:Json(name = "room") val room: FFZRoomDto,
    @field:Json(name = "sets") val sets: Map<String, FFZEmoteSetDto>
)

@Keep
data class FFZGlobalDto(
    @field:Json(name = "sets") val sets: Map<String, FFZEmoteSetDto>
)