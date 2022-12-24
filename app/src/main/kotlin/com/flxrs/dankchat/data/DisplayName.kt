package com.flxrs.dankchat.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
@Parcelize
value class DisplayName(val value: String) : Parcelable{
    override fun toString(): String {
        return value
    }
}

fun DisplayName.toUserName() = UserName(value)
fun String.toDisplayName() = DisplayName(this)