package com.flxrs.dankchat.preferences.developer

import kotlinx.serialization.Serializable

@Serializable
data class DeveloperSettings(
    val debugMode: Boolean = false,
    val repeatedSending: Boolean = false,
    val bypassCommandHandling: Boolean = false,
    val customRecentMessagesHost: String = RM_HOST_DEFAULT,
) {
    companion object {
        const val RM_HOST_DEFAULT = "https://recent-messages.robotty.de/api/v2/"
    }
}
