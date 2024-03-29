package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class UserFollowsDto(
    @SerialName(value = "total") val total: Int,
    @SerialName(value = "data") val data: List<UserFollowsDataDto>
)
