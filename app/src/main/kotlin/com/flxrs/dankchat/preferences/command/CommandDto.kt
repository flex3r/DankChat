package com.flxrs.dankchat.preferences.command

import kotlinx.serialization.Serializable

@Serializable
data class CommandDto(val trigger: String, val command: String) {
    companion object {
        fun CommandItem.Entry.toDto() = CommandDto(trigger, command)
        fun CommandDto.toEntryItem() = CommandItem.Entry(trigger, command)
    }
}
