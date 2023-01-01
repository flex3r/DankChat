package com.flxrs.dankchat.data.api.chatters.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ChattersDto(
    @SerialName(value = "broadcaster") val broadcaster: List<String>,
    @SerialName(value = "vips") val vips: List<String>,
    @SerialName(value = "moderators") val moderators: List<String>,
    @SerialName(value = "viewers") val viewers: List<String>
) {
    val total: List<String>
        get() = viewers + vips + moderators + broadcaster
}

