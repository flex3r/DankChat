package com.flxrs.dankchat.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
@Parcelize
value class UserName(val value: String) : Parcelable {
    override fun toString(): String {
        return value
    }

    fun lowercase() = UserName(value.lowercase())

    fun formatWithDisplayName(displayName: DisplayName): String = when {
        matches(displayName) -> displayName.value
        else                 -> "$this($displayName)"
    }

    fun valueOrDisplayName(displayName: DisplayName): String = when {
        matches(displayName) -> displayName.value
        else                 -> this.value
    }

    fun matches(other: String) = value.equals(other, ignoreCase = true)
    fun matches(other: UserName) = value.equals(other.value, ignoreCase = true)
    fun matches(other: DisplayName) = value.equals(other.value, ignoreCase = true)
    fun matches(regex: Regex) = value.matches(regex)
}

fun UserName.toDisplayName() = DisplayName(value)
fun String.toUserName() = UserName(this)
fun Collection<String>.toUserNames() = map(String::toUserName)