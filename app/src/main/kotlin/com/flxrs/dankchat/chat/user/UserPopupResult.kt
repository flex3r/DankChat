package com.flxrs.dankchat.chat.user

import android.os.Parcelable
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import kotlinx.parcelize.Parcelize

sealed interface UserPopupResult : Parcelable {
    @Parcelize
    data class Error(val throwable: Throwable?) : UserPopupResult

    @Parcelize
    data class Whisper(val targetUser: UserName) : UserPopupResult

    @Parcelize
    data class Mention(val targetUser: UserName, val targetDisplayName: DisplayName) : UserPopupResult
}
