package com.flxrs.dankchat.data.api.dankchat.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class DankChatBadgeDto(
    @SerialName(value = "id") val id: String,
    @SerialName(value = "title") val title: String,
    @SerialName(value = "url") val url: String,
)
