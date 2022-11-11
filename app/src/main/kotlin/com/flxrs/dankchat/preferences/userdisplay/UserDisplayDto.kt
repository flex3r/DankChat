package com.flxrs.dankchat.preferences.userdisplay

import kotlinx.serialization.Serializable

@Serializable
data class UserDisplayDto(val id: Int, val username: String, val colorHex: String, val alias: String) {
    companion object {
        // when entering text, it's possible that there is leading/trailing whitespace due to user's keyboard inserting space after word, trim them
        fun UserDisplayItem.Entry.toDto() = UserDisplayDto(id, username.trim(), colorHex.trim(), alias.trim())
        fun UserDisplayDto.toEntryItem() = UserDisplayItem.Entry(id, username, colorHex, alias)
    }
}