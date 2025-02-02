package com.flxrs.dankchat.preferences.notifications.ignores

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow

sealed interface IgnoreEvent {
    data class ItemRemoved(val item: IgnoreItem, val position: Int) : IgnoreEvent
    data class ItemAdded(val position: Int, val isLast: Boolean) : IgnoreEvent
    data class BlockError(val item: TwitchBlockItem) : IgnoreEvent
    data class UnblockError(val item: TwitchBlockItem) : IgnoreEvent
}

@Stable
data class IgnoreEventsWrapper(val events: Flow<IgnoreEvent>)
