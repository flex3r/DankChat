package com.flxrs.dankchat.preferences.notifications

import kotlinx.serialization.Serializable

sealed interface NotificationsSettingsRoute {
    @Serializable
    data object NotificationsSettings : NotificationsSettingsRoute

    @Serializable
    data object Highlights : NotificationsSettingsRoute

    @Serializable
    data object Ignores : NotificationsSettingsRoute
}
