package com.flxrs.dankchat.data.twitch.message

import androidx.annotation.ColorInt
import com.flxrs.dankchat.data.database.UserDisplayEntity

/** represent final effect UserDisplay (after considering enabled/disabled states) */
data class UserDisplay(val alias: String?, val color: Int?)

fun UserDisplayEntity.toUserDisplay() = UserDisplay(
    alias = alias?.takeIf { aliasEnabled && it.isNotBlank() },
    color = color.takeIf { colorEnabled },
)

@ColorInt
fun UserDisplay?.colorOrElse(@ColorInt fallback: Int): Int = this?.color ?: fallback


