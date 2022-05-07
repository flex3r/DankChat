package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ValidateUserDto(
    @SerialName(value = "client_id") val clientId: String,
    @SerialName(value = "login") val login: String,
    @SerialName(value = "scopes") val scopes: List<String>, // TODO Verify scopes
    @SerialName(value = "user_id") val userId: String
)

@Keep
@Serializable
data class HelixUsersDto(
    @SerialName(value = "data") val data: List<HelixUserDto>
)

@Keep
@Serializable
data class HelixUserDto(
    @SerialName(value = "id") val id: String,
    @SerialName(value = "login") val name: String,
    @SerialName(value = "display_name") val displayName: String,
    @SerialName(value = "type") val type: String,
    @SerialName(value = "broadcaster_type") val broadcasterType: String,
    @SerialName(value = "description") val description: String,
    @SerialName(value = "profile_image_url") val avatarUrl: String,
    @SerialName(value = "offline_image_url") val offlineImageUrl: String,
    @SerialName(value = "view_count") val viewCount: Int,
    @SerialName(value = "created_at") val createdAt: String
)

@Keep
@Serializable
data class HelixUserBlockListDto(
    @SerialName(value = "data") val data: List<HelixUserBlockDto>
)

@Keep
@Serializable
data class HelixUserBlockDto(
    @SerialName(value = "user_id") val id: String
)
