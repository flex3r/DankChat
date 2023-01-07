package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.UserName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class StreamDto(
    @SerialName(value = "viewer_count") val viewerCount: Int,
    @SerialName(value = "user_login") val userLogin: UserName,
    @SerialName(value = "started_at") val startedAt: String,
)
