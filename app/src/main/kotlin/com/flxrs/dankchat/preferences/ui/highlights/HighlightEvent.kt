package com.flxrs.dankchat.preferences.ui.highlights

sealed class HighlightEvent {
    data class ItemRemoved(val item: HighlightItem, val position: Int) : HighlightEvent()
}