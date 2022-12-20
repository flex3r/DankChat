package com.flxrs.dankchat.preferences.userdisplay

import android.content.Context
import com.flxrs.dankchat.data.database.UserDisplayEntity
import com.flxrs.dankchat.utils.extensions.getContrastTextColor
import com.flxrs.dankchat.utils.extensions.toHexCode

sealed class UserDisplayItem {
    data class Entry(
        val id: Int,
        var username: String,
        var enabled: Boolean,
        var colorEnabled: Boolean,
        var color: Int, // color need to be opaque
        var aliasEnabled: Boolean,
        var alias: String
    ) : UserDisplayItem()


    fun Entry.toEntity() = UserDisplayEntity(
        id = id,
        // prevent whitespace before/after name from messing up with matching
        targetUser = username.trim(),
        enabled = enabled,
        colorEnabled = colorEnabled,
        color = color,
        aliasEnabled = aliasEnabled,
        alias = alias
    )

    fun UserDisplayEntity.toEntry() = Entry(
        id = id,
        // prevent whitespace before/after name from messing up with matching
        username = targetUser.trim(),
        enabled = enabled,
        colorEnabled = colorEnabled,
        color = color,
        aliasEnabled = aliasEnabled,
        alias = alias.orEmpty()
    )

    object AddEntry : UserDisplayItem()

}

val UserDisplayItem.Entry.displayColor: String get() = "#" + color.toHexCode()

/** get Text color (on top of background), Context passed in will be used to determine tint on "white" and "black" color*/
fun UserDisplayItem.Entry.textColor(context: Context) = color.getContrastTextColor(context)

