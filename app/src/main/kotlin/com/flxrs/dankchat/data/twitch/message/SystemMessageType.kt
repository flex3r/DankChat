package com.flxrs.dankchat.data.twitch.message

sealed class SystemMessageType {
    object Connected : SystemMessageType()
    object Disconnected : SystemMessageType()
    object NoHistoryLoaded : SystemMessageType()
    object LoginExpired : SystemMessageType()
    data class Custom(val message: String) : SystemMessageType()
}