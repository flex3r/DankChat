package com.flxrs.dankchat.service.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

sealed class SupibotDtos {

    @Keep
    data class Commands(@field:Json(name = "data") val data: List<Command>)

    @Keep
    data class Command(@field:Json(name = "name") val name: String, @field:Json(name = "aliases") val aliases: List<String>)

    @Keep
    data class Channels(@field:Json(name = "data") val data: List<Channel>)

    @Keep
    data class Channel(@field:Json(name = "name") val name: String, @field:Json(name = "mode") val mode: String, @field:Json(name = "platformName") val platform: String) {
        fun isActive() = mode != "Last seen" && mode != "Read"
    }
}