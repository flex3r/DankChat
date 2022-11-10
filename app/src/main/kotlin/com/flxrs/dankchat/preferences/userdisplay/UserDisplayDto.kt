package com.flxrs.dankchat.preferences.userdisplay

import kotlinx.serialization.Serializable

@Serializable
data class UserDisplayDto(val username: String, val colorHex: String) {
    companion object {
        fun UserDisplayItem.Entry.toDto() = UserDisplayDto(username, colorHex)
        fun UserDisplayDto.toEntryItem() = UserDisplayItem.Entry(username, colorHex)
    }
}