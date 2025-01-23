package com.flxrs.dankchat.preferences.chat

import kotlinx.serialization.Serializable

sealed interface ChatSettingsRoute {
    @Serializable
    data object ChatSettings : ChatSettingsRoute

    @Serializable
    data object Commands : ChatSettingsRoute

    @Serializable
    data object UserDisplay : ChatSettingsRoute
}
