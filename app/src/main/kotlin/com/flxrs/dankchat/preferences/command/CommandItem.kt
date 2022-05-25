package com.flxrs.dankchat.preferences.command


sealed class CommandItem {

    data class Entry(var trigger: String, var command: String) : CommandItem()

    object AddEntry : CommandItem()
}
