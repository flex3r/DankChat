package com.flxrs.dankchat.preferences.userdisplay

import android.graphics.Color
import androidx.annotation.ColorInt

/** finalized user display (based on current display and override) */
data class UserFinalizedDisplay(val username: String, val displayName: String, @ColorInt val color: Int) {
    companion object {
        fun calculateUserDisplay(override: UserDisplayDto, origName: String, origDisplayName: String?, @ColorInt origColor: Int): UserFinalizedDisplay {
            // if override name: show overriden name with orginal name (login) in parenthesis (so user can know the mention)
            val finalName = if (override.alias.isNotEmpty()) origName else override.alias
            val finalDisplayName = if (override.alias.isNotEmpty()) origName else origDisplayName.orEmpty()
            val finalColor = kotlin.runCatching { Color.parseColor(override.colorHex) }.getOrElse { origColor }
            return UserFinalizedDisplay(finalName, finalDisplayName, finalColor)
        }

    }

}