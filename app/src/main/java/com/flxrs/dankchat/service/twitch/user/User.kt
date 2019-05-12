package com.flxrs.dankchat.service.twitch.user

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class User(
		val _id: Int,
		val bio: String,
		val created_at: String,
		val display_name: String,
		val email: String,
		val email_verified: Boolean,
		val logo: String,
		val name: String,
		val notifications: Map<String, Boolean>,
		val partnered: Boolean,
		val twitter_connected: Boolean,
		val type: String,
		val updated_at: String
)