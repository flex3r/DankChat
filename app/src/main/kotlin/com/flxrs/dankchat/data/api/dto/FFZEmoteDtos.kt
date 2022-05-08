package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class FFZEmoteDto(
    @SerialName(value = "urls") val urls: Map<String, String?>,
    @SerialName(value = "name") val name: String,
    @SerialName(value = "id") val id: Int
)

@Keep
@Serializable
data class FFZEmoteSetDto(@SerialName(value = "emoticons") val emotes: List<FFZEmoteDto>)

@Keep
@Serializable
data class FFZRoomDto(
    @SerialName(value = "mod_urls") val modBadgeUrls: Map<String, String?>?,
    @SerialName(value = "vip_badge") val vipBadgeUrls: Map<String, String?>?,
)

@Keep
@Serializable
data class FFZChannelDto(
    @SerialName(value = "room") val room: FFZRoomDto,
    @SerialName(value = "sets") val sets: Map<String, FFZEmoteSetDto>
)

@Keep
@Serializable
data class FFZGlobalDto(@SerialName(value = "sets") val sets: Map<String, FFZEmoteSetDto>)