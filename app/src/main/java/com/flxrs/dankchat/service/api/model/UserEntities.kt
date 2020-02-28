package com.flxrs.dankchat.service.api.model

import androidx.annotation.Keep
import com.squareup.moshi.Json

sealed class UserEntities {

    @Keep
    data class ValidateUser(
        @field:Json(name = "client_id") val clientId: String,
        @field:Json(name = "login") val login: String,
        @field:Json(name = "scopes") val scopes: List<String>, // TODO Verify scopes
        @field:Json(name = "user_id") val userId: Int
    )

    @Keep
    data class HelixUser(
        @field:Json(name = "id") val id: String,
        @field:Json(name = "login") val name: String,
        @field:Json(name = "display_name") val displayName: String,
        @field:Json(name = "type") val type: String,
        @field:Json(name = "broadcaster_type") val broadcasterType: String,
        @field:Json(name = "description") val description: String,
        @field:Json(name = "profile_image_url") val avatarUrl: String,
        @field:Json(name = "offline_image_url") val offlineImageUrl: String,
        @field:Json(name = "view_count") val viewCount: Int
    )

    // R8 deletes this if annotation is not set NotLikeThis
    @Keep
    data class KrakenUser(@field:Json(name = "_id") val id: Int)
    @Keep
    data class KrakenUserEntry(@field:Json(name = "user") val user: KrakenUser)
    @Keep
    data class KrakenUsersBlocks(@field:Json(name = "blocks") val blocks: List<KrakenUserEntry>)
    @Keep
    data class HelixUsers(@field:Json(name = "data") val data: List<HelixUser>)
}
