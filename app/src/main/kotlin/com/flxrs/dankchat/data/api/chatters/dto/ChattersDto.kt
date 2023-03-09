package com.flxrs.dankchat.data.api.chatters.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.UserName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ChattersDto(
    @SerialName(value = "broadcaster") val broadcaster: List<UserName>,
    @SerialName(value = "vips") val vips: List<UserName>,
    @SerialName(value = "moderators") val moderators: List<UserName>,
    @SerialName(value = "viewers") val viewers: List<UserName>
) {
    val total: List<UserName>
        get() = viewers + vips + moderators + broadcaster

    companion object {
        val EMPTY = ChattersDto(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

