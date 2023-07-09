package com.flxrs.dankchat.preferences.ui.userdisplay

sealed interface UserDisplayEvent {
    data class ItemRemoved(val item: UserDisplayItem.Entry) : UserDisplayEvent
}
