package com.flxrs.dankchat.preferences.developer

import kotlinx.serialization.Serializable

@Serializable
data class DeveloperSettings(
    val debugMode: Boolean = false,
    val repeatedSending: Boolean = false,
    val bypassCommandHandling: Boolean = false,
    val customRecentMessagesHost: String = RM_HOST_DEFAULT,
    val eventSubEnabled: Boolean = true,
    val eventSubDebugOutput: Boolean = false,
) {

    val isPubSubShutdown: Boolean get() = System.currentTimeMillis() > PUBSUB_SHUTDOWN_MILLIS
    val shouldUseEventSub: Boolean get() = eventSubEnabled || isPubSubShutdown
    val shouldUsePubSub: Boolean get() = !shouldUseEventSub

    companion object {
        private const val PUBSUB_SHUTDOWN_MILLIS = 1744653600000 // 2025-04-14T18:00:00.000Z
        const val RM_HOST_DEFAULT = "https://recent-messages.robotty.de/api/v2/"
    }
}
