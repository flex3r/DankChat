package com.flxrs.dankchat.chat.user

import java.time.ZonedDateTime

data class User(
    val userId: String,
    val userName: String,
    val displayName: String,
    val created: String,
    val avatarUrl: String
)

data class UserFollows(
    val isFollowing: Boolean = false,
    val followingSince: String? = null
)
