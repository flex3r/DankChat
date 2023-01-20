package com.flxrs.dankchat.data.api.dankchat.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class DankChatBadgeDto(
    @SerialName(value = "type") val type: String,
    @SerialName(value = "url") val url: String,
    @SerialName(value = "users") val users: List<UserId>
)