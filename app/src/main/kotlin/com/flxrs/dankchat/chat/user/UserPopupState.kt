package com.flxrs.dankchat.chat.user

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName

sealed class UserPopupState {
    data class Loading(val userName: UserName, val displayName: DisplayName) : UserPopupState()
    data class Error(val throwable: Throwable? = null) : UserPopupState()
    data class Success(
        val userId: UserId,
        val userName: UserName,
        val displayName: DisplayName,
        val created: String,
        val avatarUrl: String,
        val isFollowing: Boolean = false,
        val followingSince: String? = null,
        val isBlocked: Boolean = false
    ) : UserPopupState()

    data class NotLoggedIn(val userName: UserName, val displayName: DisplayName) : UserPopupState()
}