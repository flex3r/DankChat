package com.flxrs.dankchat.preferences.userdisplay

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

    object AddEntry : UserDisplayItem()


}