package com.flxrs.dankchat.preferences.userdisplay

sealed class UserDisplayEvent {
    data class ItemRemoved(val item: UserDisplayItem.Entry) : UserDisplayEvent()
}