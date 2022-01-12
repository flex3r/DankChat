package com.flxrs.dankchat.chat.user

sealed class UserPopupState {
    object Loading : UserPopupState()
    data class Error(val throwable: Throwable? = null) : UserPopupState()
    data class Success(
        val userId: String,
        val userName: String,
        val displayName: String,
        val created: String,
        val avatarUrl: String,
        val isFollowing: Boolean = false,
        val followingSince: String? = null,
        val isBlocked: Boolean = false
    ) : UserPopupState()
    data class NotLoggedIn(val userName: String) : UserPopupState()
}