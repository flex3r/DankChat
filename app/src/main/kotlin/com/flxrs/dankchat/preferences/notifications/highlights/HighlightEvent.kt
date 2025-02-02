package com.flxrs.dankchat.preferences.notifications.highlights

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow

sealed interface HighlightEvent {
    data class ItemRemoved(val item: HighlightItem, val position: Int) : HighlightEvent
    data class ItemAdded(val position: Int, val isLast: Boolean) : HighlightEvent
}

@Stable
data class HighlightEventsWrapper(val events: Flow<HighlightEvent>)
