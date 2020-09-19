package com.flxrs.dankchat.service.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

sealed class ChattersDto {

    @Keep
    data class Chatters(
        @field:Json(name = "broadcaster") val broadcaster: List<String>,
        @field:Json(name = "vips") val vips: List<String>,
        @field:Json(name = "moderators") val moderators: List<String>,
        @field:Json(name = "viewers") val viewers: List<String>
    ) {
        val total: List<String>
            get() = viewers + vips + moderators + broadcaster
    }

    @Keep
    data class Result(@field:Json(name = "chatters") val chatters: Chatters)
}