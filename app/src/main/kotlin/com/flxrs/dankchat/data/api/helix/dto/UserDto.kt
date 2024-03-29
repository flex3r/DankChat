package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class UserDto(
    @SerialName(value = "id") val id: UserId,
    @SerialName(value = "login") val name: UserName,
    @SerialName(value = "display_name") val displayName: DisplayName,
    @SerialName(value = "type") val type: String,
    @SerialName(value = "broadcaster_type") val broadcasterType: String,
    @SerialName(value = "description") val description: String,
    @SerialName(value = "profile_image_url") val avatarUrl: String,
    @SerialName(value = "offline_image_url") val offlineImageUrl: String,
    @SerialName(value = "view_count") val viewCount: Int,
    @SerialName(value = "created_at") val createdAt: String
)
