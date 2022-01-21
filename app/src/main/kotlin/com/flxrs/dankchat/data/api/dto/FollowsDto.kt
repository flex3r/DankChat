package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class UserFollowsDto(
    @field:Json(name = "total") val total: Int,
    @field:Json(name = "data") val data: List<UserFollowsDataDto>
)

@Keep
data class UserFollowsDataDto(
    @field:Json(name = "followed_at") val followedAt: String
)

@Keep
data class UserFollowRequestBody(val from_id: String, val to_id: String)