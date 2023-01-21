package com.flxrs.dankchat.preferences.ui.userdisplay

sealed class UserDisplayEvent {
    data class ItemRemoved(val item: UserDisplayItem.Entry) : UserDisplayEvent()
}
