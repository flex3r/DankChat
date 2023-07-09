package com.flxrs.dankchat.preferences.ui.ignores

sealed interface IgnoreEvent {
    data class ItemRemoved(val item: IgnoreItem, val position: Int) : IgnoreEvent
    data class BlockError(val item: TwitchBlockItem) : IgnoreEvent
    data class UnblockError(val item: TwitchBlockItem) : IgnoreEvent
}
