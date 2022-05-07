package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class StreamsDto(
    @SerialName(value = "data") val data: List<StreamDataDto>
)


@Keep
@Serializable
data class StreamDataDto(
    @SerialName(value = "viewer_count") val viewerCount: Int,
    @SerialName(value = "user_login") val userLogin: String,
    @SerialName(value = "started_at") val startedAt: String,
)
