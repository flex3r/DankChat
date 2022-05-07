package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class RecentMessagesDto(
    @SerialName(value = "messages")
    @field:Json(name = "messages")
    val messages: List<String>?,

    @SerialName(value = "error_code")
    @field:Json(name = "error_code")
    val errorCode: String?,
) {
    companion object {
        const val ERROR_CHANNEL_NOT_JOINED = "channel_not_joined"
        const val ERROR_CHANNEL_IGNORED = "channel_ignored"
    }
}