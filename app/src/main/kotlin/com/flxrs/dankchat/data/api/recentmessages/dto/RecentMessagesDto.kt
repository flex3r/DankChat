package com.flxrs.dankchat.data.api.recentmessages.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class RecentMessagesDto(
    @SerialName(value = "messages")
    val messages: List<String>?,
    @SerialName(value = "error")
    val error: String?,
    @SerialName(value = "error_code")
    val errorCode: String?,
) {
    companion object {
        const val ERROR_CHANNEL_NOT_JOINED = "channel_not_joined"
        const val ERROR_CHANNEL_IGNORED = "channel_ignored"
    }
}