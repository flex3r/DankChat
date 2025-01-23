package com.flxrs.dankchat.preferences.chat.userdisplay

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow

sealed interface UserDisplayEvent {
    data class ItemRemoved(val removed: UserDisplayItem) : UserDisplayEvent
}

@Stable
data class UserDisplayEventsWrapper(val events: Flow<UserDisplayEvent>)
