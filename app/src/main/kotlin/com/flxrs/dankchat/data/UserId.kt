package com.flxrs.dankchat.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
@Parcelize
value class UserId(val value: String) : Parcelable {
    override fun toString() = value
}

fun String.toUserId() = UserId(this)
inline fun UserId.ifBlank(default: () -> UserId?): UserId? {
    return if (value.isBlank()) default() else this
}