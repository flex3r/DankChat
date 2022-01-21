package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class SupibotCommandsDto(@field:Json(name = "data") val data: List<SupibotCommandDto>)

@Keep
data class SupibotCommandDto(@field:Json(name = "name") val name: String, @field:Json(name = "aliases") val aliases: List<String>)

@Keep
data class SupibotChannelsDto(@field:Json(name = "data") val data: List<SupibotChannelDto>)

@Keep
data class SupibotChannelDto(@field:Json(name = "name") val name: String, @field:Json(name = "mode") val mode: String) {
    fun isActive() = mode != "Last seen" && mode != "Read"
}