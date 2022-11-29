package com.flxrs.dankchat.preferences.userdisplay

import com.flxrs.dankchat.data.database.UserDisplayEntity

sealed class UserDisplayItem {
    // NOTE: displayName can be omitted (left empty)
    data class Entry(
        val id: Int,
        var username: String,
        var enabled: Boolean = true,
        var colorEnabled: Boolean = false,
        var color: Int = 0,
        var aliasEnabled: Boolean = false,
        var alias: String = "",
    ) : UserDisplayItem()


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