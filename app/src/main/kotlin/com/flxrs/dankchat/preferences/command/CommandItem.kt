package com.flxrs.dankchat.preferences.command

import kotlinx.serialization.Serializable

sealed class CommandItem {

    @Serializable
    data class Entry(var trigger: String, var command: String) : CommandItem()

    object AddEntry : CommandItem()
}
