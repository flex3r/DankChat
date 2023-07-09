package com.flxrs.dankchat.preferences.ui.tts

sealed interface TtsIgnoreItem {
    data class Entry(var user: String) : TtsIgnoreItem
    data object AddEntry : TtsIgnoreItem
}
