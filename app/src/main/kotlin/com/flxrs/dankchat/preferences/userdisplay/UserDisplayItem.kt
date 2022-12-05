@file:OptIn(ExperimentalContracts::class)

package com.flxrs.dankchat.preferences.userdisplay

import android.content.Context
import androidx.annotation.ColorInt
import com.flxrs.dankchat.data.database.UserDisplayEntity
import com.flxrs.dankchat.utils.extensions.getContrastTextColor
import com.flxrs.dankchat.utils.extensions.toHexCode
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

data class UserDisplayEffectiveValue(val alias: String?, val color: Int?)

sealed class UserDisplayItem {
    // NOTE: displayName can be omitted (left empty)
    data class Entry(
        val id: Int,
        var username: String,
        var enabled: Boolean = true,
        var colorEnabled: Boolean = false,
        var color: Int = 0xff000000.toInt(), // color need to be opaque
        var aliasEnabled: Boolean = false,
        var alias: String = "",
    ) : UserDisplayItem() {

        //        /** calculate final effect of this UserDisplay */
        fun effectiveValue() = UserDisplayEffectiveValue(
            alias = if (aliasEnabled && alias.isNotBlank()) alias else null, // prevent blank alias from making username blank (fool-proof)
            color = if (colorEnabled) color else null
        )
    }


    fun Entry.toEntity() = UserDisplayEntity(
        id = id,
        targetUser = username,
        enabled = enabled,
        colorEnabled = colorEnabled,
        color = color,
        aliasEnabled = aliasEnabled,
        alias = alias
    )

    fun UserDisplayEntity.toEntry() = Entry(
        id = id,
        username = targetUser,
        enabled = enabled,
        colorEnabled = colorEnabled,
        color = color,
        aliasEnabled = aliasEnabled,
        alias = alias.orEmpty()
    )

    object AddEntry : UserDisplayItem()

}

val UserDisplayItem.Entry.displayText: String get() = "#" + color.toHexCode()

/** get Text color (on top of background), Context passed in will be used to determine tint on "white" and "black" color*/
fun UserDisplayItem.Entry.textColor(context: Context? = null) = color.getContrastTextColor(context)

// convenient functions for null/empty checking, also callable on null receiver
fun UserDisplayEffectiveValue?.hasAlias(): Boolean {
    contract {
        returns(true) implies (this@hasAlias != null)
    }
    return this != null && alias != null
}


fun UserDisplayEffectiveValue?.nameOr(fallback: String): String {
    return if (hasAlias()) alias!! else fallback
}

@ColorInt
fun UserDisplayEffectiveValue?.colorOr(@ColorInt fallback: Int): Int {
    return if (hasColor()) color!! else fallback
}


fun UserDisplayEffectiveValue?.hasColor(): Boolean {
    contract {
        returns(true) implies (this@hasColor != null)
    }
    return this != null && color != null
}
