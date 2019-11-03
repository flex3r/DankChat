package com.flxrs.dankchat.preferences.multientry

sealed class MultiEntryItem {

    data class Entry(var entry: String, var isRegex: Boolean) : MultiEntryItem()

    object AddEntry : MultiEntryItem()

    override fun equals(other: Any?): Boolean {
        if (javaClass != other?.javaClass) return false
        if (this is Entry && other is Entry) {
            if (this.entry.isBlank() && other.entry.isBlank()) {
                return false
            }
        }
        return this === other
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}