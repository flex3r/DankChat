package com.flxrs.dankchat.chat.user

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class UserPopupResult : Parcelable {
    @Parcelize
    data class Error(val throwable: Throwable?) : UserPopupResult()

    @Parcelize
    data class Whisper(val targetUser: String) : UserPopupResult()

    @Parcelize
    data class Mention(val targetUser: String) : UserPopupResult()
}