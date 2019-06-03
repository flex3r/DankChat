package com.flxrs.dankchat.service.api.model

import com.squareup.moshi.Json

sealed class UserEntity {

	data class FromKraken(@field:Json(name = "_id") val id: Int,
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
						  @field:Json(name = "updated_at") val lastUpdated: String) : UserEntity()

	data class FromHelix(@field:Json(name = "id") val id: String,
						 @field:Json(name = "login") val name: String,
						 @field:Json(name = "display_name") val displayName: String,
						 @field:Json(name = "type") val type: String,
						 @field:Json(name = "broadcaster_type") val broadcasterType: String,
						 @field:Json(name = "description") val description: String,
						 @field:Json(name = "profile_image_url") val avatarUrl: String,
						 @field:Json(name = "offline_image_url") val offlineImageUrl: String,
						 @field:Json(name = "view_count") val viewCount: Int) : UserEntity()

	data class FromHelixAsArray(@field:Json(name = "data") val data: Array<UserEntity.FromHelix>) : UserEntity() {
		override fun hashCode(): Int = data.contentHashCode()
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (javaClass != other?.javaClass) return false

			other as FromHelixAsArray
			if (!data.contentEquals(other.data)) return false

			return true
		}
	}
}
