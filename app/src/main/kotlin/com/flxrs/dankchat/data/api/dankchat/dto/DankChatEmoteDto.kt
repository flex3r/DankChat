package com.flxrs.dankchat.data.api.dankchat.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class DankChatEmoteDto(
    @SerialName(value = "code") val name: String,
    @SerialName(value = "id") val id: String,
    @SerialName(value = "type") val type: String?,
    @SerialName(value = "assetType") val assetType: String?,
)