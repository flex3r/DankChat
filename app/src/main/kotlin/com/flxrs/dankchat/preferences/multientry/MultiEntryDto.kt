package com.flxrs.dankchat.preferences.multientry

import kotlinx.serialization.Serializable

@Serializable
data class MultiEntryDto(val entry: String, val isRegex: Boolean, val matchUser: Boolean) {
    companion object {
        fun MultiEntryItem.Entry.toDto() = MultiEntryDto(entry, isRegex, matchUser)
        fun MultiEntryDto.toEntryItem() = MultiEntryItem.Entry(entry, isRegex, matchUser)
    }
}