package com.flxrs.dankchat.service.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class ValidateUserDto(
    @field:Json(name = "client_id") val clientId: String,
    @field:Json(name = "login") val login: String,
    @field:Json(name = "scopes") val scopes: List<String>, // TODO Verify scopes
    @field:Json(name = "user_id") val userId: String
)

@Keep
data class HelixUsersDto(
    @field:Json(name = "data") val data: List<HelixUserDto>
)

@Keep
data class HelixUserDto(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "login") val name: String,
    @field:Json(name = "display_name") val displayName: String,
    @field:Json(name = "type") val type: String,
    @field:Json(name = "broadcaster_type") val broadcasterType: String,
    @field:Json(name = "description") val description: String,
    @field:Json(name = "profile_image_url") val avatarUrl: String,
    @field:Json(name = "offline_image_url") val offlineImageUrl: String,
    @field:Json(name = "view_count") val viewCount: Int,
    @field:Json(name = "created_at") val createdAt: String
)

@Keep
data class HelixUserBlockListDto(
    @field:Json(name = "data") val data: List<HelixUserBlockDto>
)

@Keep
data class HelixUserBlockDto(
    @field:Json(name = "user_id") val id: String
)
