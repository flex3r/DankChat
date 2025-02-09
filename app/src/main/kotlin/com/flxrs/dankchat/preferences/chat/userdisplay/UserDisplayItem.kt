package com.flxrs.dankchat.preferences.chat.userdisplay

import com.flxrs.dankchat.data.database.entity.UserDisplayEntity
import com.flxrs.dankchat.utils.extensions.hexCode

data class UserDisplayItem(
    val id: Int,
    val username: String,
    val enabled: Boolean,
    val colorEnabled: Boolean,
    val color: Int, // color needs to be opaque
    val aliasEnabled: Boolean,
    val alias: String
)

fun UserDisplayItem.toEntity() = UserDisplayEntity(
    id = id,
    // prevent whitespace before/after name from messing up with matching
    targetUser = username.trim(),
    enabled = enabled,
    colorEnabled = colorEnabled,
    color = color,
    aliasEnabled = aliasEnabled,
    alias = alias.ifEmpty { null }
)

fun UserDisplayEntity.toItem() = UserDisplayItem(
    id = id,
    username = targetUser,
    enabled = enabled,
    colorEnabled = colorEnabled,
    color = color,
    aliasEnabled = aliasEnabled,
    alias = alias.orEmpty()
)

val UserDisplayItem.formattedDisplayColor: String get() = "#" + color.hexCode

