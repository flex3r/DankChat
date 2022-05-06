package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class RecentMessagesDto(
    @field:Json(name = "messages") val messages: List<String>?,
    @field:Json(name = "error_code") val errorCode: String?,
) {
    companion object {
        const val ERROR_CHANNEL_NOT_JOINED = "channel_not_joined"
    }
}