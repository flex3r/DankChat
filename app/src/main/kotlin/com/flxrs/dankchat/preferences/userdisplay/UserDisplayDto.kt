@file:OptIn(ExperimentalContracts::class)

package com.flxrs.dankchat.preferences.userdisplay

import androidx.annotation.ColorInt
import com.flxrs.dankchat.data.database.UserDisplayEntity
import kotlinx.serialization.Serializable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@Serializable
data class UserDisplayDto(
    val id: Int,
    val username: String,
    val enabled: Boolean,
    val colorEnabled: Boolean,
    val color: Int,
    val aliasEnabled: Boolean,
    val alias: String,
) {
    companion object {
        fun UserDisplayItem.Entry.toDto() = UserDisplayDto(
            id = id,
            // trim input entries (especially important for username matching)
            username = username.trim(),
            enabled = enabled,
            colorEnabled = colorEnabled,
            color = color,
            aliasEnabled = aliasEnabled,
            alias = alias.trim(),
        )

        fun UserDisplayDto.toEntryItem() = UserDisplayItem.Entry(
            id = id,
            username = username,
            enabled = enabled,
            colorEnabled = colorEnabled,
            color = color,
            aliasEnabled = aliasEnabled,
            alias = alias,
        )

        fun UserDisplayEntity.toDto() = UserDisplayDto(
            id = id,
            username = targetUser,
            enabled = enabled,
            colorEnabled = colorEnabled,
            color = color,
            aliasEnabled = aliasEnabled,
            alias = alias.orEmpty()
        )
    }

    fun toEntity() = UserDisplayEntity(
        id = id,
        targetUser = username,
        enabled = enabled,
        colorEnabled = colorEnabled,
        color = color,
        aliasEnabled = aliasEnabled,
        alias = alias
    )
}

// these are defined as extension type to allow convenient checking even the object is null
/** return whether alias is set, and enabled
 * calling on null will return false */
fun UserDisplayDto?.hasAlias(): Boolean {
    contract {
        returns(true) implies (this@hasAlias != null)
    }
    if (this == null) return false
    return enabled && aliasEnabled && alias.isNotEmpty()
}

/** return this object's alias if has set, otherwise, fallback */
fun UserDisplayDto?.nameIfEmpty(fallback: String): String {
    return if (hasAlias()) alias else fallback
}

/** return this object's color if it enabled, hasSet and valid, otherwise, fallback */
@ColorInt
fun UserDisplayDto?.colorIfEmpty(@ColorInt fallback: Int): Int {
    return if (hasColor()) color else fallback
}


/** return whether color is set, (i.e not empty), calling on null will return false.
 * Note that this method does NOT check if the color is valid
 * */
fun UserDisplayDto?.hasColor(): Boolean {
    contract {
        returns(true) implies (this@hasColor != null)
    }
    if (this == null) return false
    return enabled && colorEnabled
}
