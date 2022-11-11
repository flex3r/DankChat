package com.flxrs.dankchat.preferences.userdisplay

sealed class UserDisplayItem {
    // NOTE: displayName can be omitted (left empty)
    data class Entry(val id: Int, var username: String, var colorHex: String, var alias: String) : UserDisplayItem()

    object AddEntry : UserDisplayItem()

}