@file:OptIn(ExperimentalContracts::class)

package com.flxrs.dankchat.preferences.userdisplay

import android.graphics.Color
import androidx.annotation.ColorInt
import com.flxrs.dankchat.data.database.UserDisplayEntity
import kotlinx.serialization.Serializable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@Serializable
data class UserDisplayDto(val id: Int, val username: String, val colorHex: String, val alias: String) {
    companion object {
        // when entering text, it's possible that there is leading/trailing whitespace due to user's keyboard inserting space after word, trim them
        fun UserDisplayItem.Entry.toDto() = UserDisplayDto(id, username.trim(), colorHex.trim(), alias.trim())
        fun UserDisplayDto.toEntryItem() = UserDisplayItem.Entry(id, username, colorHex, alias)
        fun UserDisplayEntity.toDto() = UserDisplayDto(id = id, username = targetUser, colorHex = colorHex.orEmpty(), alias = alias.orEmpty())
    }

    fun toEntity() = UserDisplayEntity(id = id, targetUser = username, colorHex = colorHex, alias = alias)

    val color: Int?
        get() = if (hasColor()) kotlin.runCatching { Color.parseColor(colorHex) }.getOrNull() else null
}

// these are defined as extension type to allow convenient checking even the object is null
/** return whether alias is set, (i.e not empty), calling on null will return false */
fun UserDisplayDto?.hasAlias(): Boolean {
    contract {
        returns(true) implies (this@hasAlias != null)
    }
    if (this == null) return false
    return alias.isNotEmpty()
}

/** return this object's alias if has set, otherwise, fallback */
fun UserDisplayDto?.nameIfEmpty(fallback: String): String {
    return if (hasAlias()) alias else fallback
}

/** return this object's color if it has set and valid, otherwise, fallback */
@ColorInt
fun UserDisplayDto?.colorIfEmpty(@ColorInt fallback: Int): Int {
    return this?.color ?: fallback
}


/** return whether color is set, (i.e not empty), calling on null will return false.
 * Note that this method does NOT check if the color is valid
 * */
fun UserDisplayDto?.hasColor(): Boolean {
    contract {
        returns(true) implies (this@hasColor != null)
    }
    if (this == null) return false
    return colorHex.isNotEmpty()
}
