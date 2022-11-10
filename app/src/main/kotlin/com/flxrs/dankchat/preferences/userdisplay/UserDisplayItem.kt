package com.flxrs.dankchat.preferences.userdisplay

sealed class UserDisplayItem {
    data class Entry(var username: String, var colorHex: String) : UserDisplayItem()

    object AddEntry : UserDisplayItem()

}