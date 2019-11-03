package com.flxrs.dankchat.preferences.multientry

sealed class MultiEntryItem {

    data class Entry(var entry: String, var isRegex: Boolean) : MultiEntryItem()

    object AddEntry : MultiEntryItem()

    override fun equals(other: Any?): Boolean {
        if (javaClass != other?.javaClass) return false
        return this === other
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}