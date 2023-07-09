package com.flxrs.dankchat.preferences.ui.userdisplay

import com.flxrs.dankchat.data.database.entity.UserDisplayEntity
import com.flxrs.dankchat.utils.extensions.hexCode

sealed interface UserDisplayItem {
    data class Entry(
        val id: Int,
        var username: String,
        var enabled: Boolean,
        var colorEnabled: Boolean,
        var color: Int, // color needs to be opaque
        var aliasEnabled: Boolean,
        var alias: String
    ) : UserDisplayItem

    data object AddEntry : UserDisplayItem
}

fun UserDisplayItem.Entry.toEntity() = UserDisplayEntity(
    id = id,
    // prevent whitespace before/after name from messing up with matching
    targetUser = username.trim(),
    enabled = enabled,
    colorEnabled = colorEnabled,
    color = color,
    aliasEnabled = aliasEnabled,
    alias = alias.ifEmpty { null }
)

fun UserDisplayEntity.toEntry() = UserDisplayItem.Entry(
    id = id,
    username = targetUser,
    enabled = enabled,
    colorEnabled = colorEnabled,
    color = color,
    aliasEnabled = aliasEnabled,
    alias = alias.orEmpty()
)

val UserDisplayItem.Entry.formattedDisplayColor: String get() = "#" + color.hexCode

