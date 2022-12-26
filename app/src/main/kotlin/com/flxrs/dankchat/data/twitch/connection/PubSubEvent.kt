package com.flxrs.dankchat.data.twitch.connection

sealed class PubSubEvent {
    data class Message(val message: PubSubMessage) : PubSubEvent()
    object Connected : PubSubEvent()
    object Error : PubSubEvent()
    object Closed : PubSubEvent()

    val isDisconnected: Boolean
        get() = this is Error || this is Closed
}