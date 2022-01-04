package com.flxrs.dankchat.service.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class StreamsDto(
    @field:Json(name = "data") val data: List<StreamDataDto>
)


@Keep
data class StreamDataDto(
    @field:Json(name = "viewer_count") val viewerCount: Int,
    @field:Json(name = "user_login") val userLogin: String,
    @field:Json(name = "started_at") val startedAt: String,
)
