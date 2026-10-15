package com.flxrs.dankchat.data.api.dankchat.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class DankChatBadgesDto(
    @SerialName(value = "badges") val badges: List<DankChatBadgeDto>,
    @SerialName(value = "users") val users: Map<UserId, String>,
)
