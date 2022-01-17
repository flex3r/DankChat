package com.flxrs.dankchat.preferences.tts

sealed class TtsIgnoreItem {
    data class Entry(var user: String) : TtsIgnoreItem()

    object AddEntry : TtsIgnoreItem()
}
