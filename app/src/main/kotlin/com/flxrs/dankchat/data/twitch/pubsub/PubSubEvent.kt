package com.flxrs.dankchat.data.twitch.pubsub

sealed interface PubSubEvent {
    data class Message(val message: PubSubMessage) : PubSubEvent
    data object Connected : PubSubEvent
    data object Error : PubSubEvent
    data object Closed : PubSubEvent

    val isDisconnected: Boolean
        get() = this is Error || this is Closed
}
