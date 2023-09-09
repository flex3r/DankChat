package com.flxrs.dankchat.preferences.model

import kotlin.time.Duration

sealed interface LiveUpdatesBackgroundBehavior {
    data object Never : LiveUpdatesBackgroundBehavior
    data class Timeout(val timeout: Duration) : LiveUpdatesBackgroundBehavior
    data object Always : LiveUpdatesBackgroundBehavior
}
