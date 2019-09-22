package com.flxrs.dankchat.service.api.model

import com.squareup.moshi.Json

sealed class UserEntities {

    data class KrakenUser(
        @field:Json(name = "_id") val id: Int,
        @field:Json(name = "bio") val bio: String,
        @field:Json(name = "created_at") val createdAt: String,
        @field:Json(name = "display_name") val displayName: String,
        @field:Json(name = "email") val email: String,
        @field:Json(name = "email_verified") val isEmailVerified: Boolean,
        @field:Json(name = "logo") val logoUrl: String,
        @field:Json(name = "name") val name: String,
        @field:Json(name = "notifications") val notifications: Map<String, Boolean>,
        @field:Json(name = "partnered") val isPartnered: Boolean,
        @field:Json(name = "twitter_connected") val isTwitterConnected: Boolean,
        @field:Json(name = "type") val type: String,
        @field:Json(name = "updated_at") val lastUpdated: String
    )

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

    data class KrakenUserEntry(@field:Json(name = "user") val user: KrakenUser)
    data class KrakenUsersBlocks(@field:Json(name = "blocks") val blocks: List<KrakenUserEntry>)
    data class HelixUsers(@field:Json(name = "data") val data: List<HelixUser>)
}
