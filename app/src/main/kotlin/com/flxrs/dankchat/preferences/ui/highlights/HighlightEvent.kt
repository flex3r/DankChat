package com.flxrs.dankchat.preferences.ui.highlights

sealed interface HighlightEvent {
    data class ItemRemoved(val item: HighlightItem, val position: Int) : HighlightEvent
}
