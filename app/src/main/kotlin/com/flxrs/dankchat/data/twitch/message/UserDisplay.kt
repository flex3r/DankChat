package com.flxrs.dankchat.data.twitch.message

import androidx.annotation.ColorInt
import com.flxrs.dankchat.data.database.entity.UserDisplayEntity

/** represent final effect UserDisplay (after considering enabled/disabled states) */
data class UserDisplay(val alias: String?, val color: Int?)

fun UserDisplayEntity.toUserDisplay() = UserDisplay(
    alias = alias?.takeIf { enabled && aliasEnabled && it.isNotBlank() },
    color = color.takeIf { enabled && colorEnabled },
)

@ColorInt
fun UserDisplay?.colorOrElse(@ColorInt fallback: Int): Int = this?.color ?: fallback


