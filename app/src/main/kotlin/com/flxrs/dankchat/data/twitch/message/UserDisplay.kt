package com.flxrs.dankchat.data.twitch.message

import androidx.annotation.ColorInt
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayItem


/** represent final effect UserDisplay (after considering enabled/disabled states) */
data class UserDisplay(val alias: String?, val color: Int?) {
    companion object {
        fun UserDisplayItem.Entry.toEffectiveValue() = UserDisplay(
            alias = if (aliasEnabled && alias.isNotBlank()) alias else null, // prevent blank alias from making username blank (fool-proof)
            color = if (colorEnabled) color else null
        )
    }
}


fun UserDisplay?.aliasOrElse(fallback: String): String {
    return if (this != null && alias != null) alias else fallback
}

@ColorInt
fun UserDisplay?.colorOrElse(@ColorInt fallback: Int): Int {
    return if (this != null && color != null) color else fallback
}


