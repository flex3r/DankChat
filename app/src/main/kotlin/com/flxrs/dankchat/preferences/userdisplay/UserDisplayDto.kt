package com.flxrs.dankchat.preferences.userdisplay

import kotlinx.serialization.Serializable

@Serializable
data class UserDisplayDto(val username: String, val colorHex: String, val alias: String) {
    companion object {
        fun UserDisplayItem.Entry.toDto() = UserDisplayDto(username, colorHex, alias)
        fun UserDisplayDto.toEntryItem() = UserDisplayItem.Entry(username, colorHex, alias)
    }
}