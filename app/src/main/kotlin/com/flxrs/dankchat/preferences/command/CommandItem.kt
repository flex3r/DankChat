package com.flxrs.dankchat.preferences.command

sealed interface CommandItem {
    data class Entry(var trigger: String, var command: String) : CommandItem
    data object AddEntry : CommandItem
}
