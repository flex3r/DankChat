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

fun String.asUserId() = UserId(this)