package com.flxrs.dankchat.preferences

sealed class Preference {
    data class RoomState(val enabled: Boolean) : Preference()
    data class StreamInfo(val enabled: Boolean, val updateTimer: Boolean) : Preference()
    data class Input(val enabled: Boolean) : Preference()
    data class CustomMentions(val entries: Set<String>) : Preference()
    data class BlackList(val entries: Set<String>) : Preference()
    data class SupibotSuggestions(val enabled: Boolean) : Preference()
    data class ScrollBack(val length: Int) : Preference()
    data class PreferEmoteSuggestions(val enabled: Boolean) : Preference()
    data class Chips(val enabled: Boolean) : Preference()
    data class TimeStampFormat(val pattern: String) : Preference()
}
