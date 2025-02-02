package com.flxrs.dankchat.preferences.chat.userdisplay

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.UserDisplayRepository
import com.flxrs.dankchat.utils.extensions.replaceAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class UserDisplayViewModel(
    private val userDisplayRepository: UserDisplayRepository
) : ViewModel() {

    private val eventChannel = Channel<UserDisplayEvent>(Channel.BUFFERED)

    val events = eventChannel.receiveAsFlow()
    val userDisplays = SnapshotStateList<UserDisplayItem>()

    fun fetchUserDisplays() {
        val items = userDisplayRepository.userDisplays.value.map { it.toItem() }
        userDisplays.replaceAll(items)
    }

    fun addUserDisplay() = viewModelScope.launch {
        val entity = userDisplayRepository.addUserDisplay()
        userDisplays += entity.toItem()
        val position = userDisplays.lastIndex
        sendEvent(UserDisplayEvent.ItemAdded(position, isLast = true))
    }

    fun addUserDisplayItem(item: UserDisplayItem, position: Int) = viewModelScope.launch {
        userDisplayRepository.updateUserDisplay(item.toEntity())
        userDisplays.add(position, item)
        val isLast = position == userDisplays.lastIndex
        sendEvent(UserDisplayEvent.ItemAdded(position, isLast))
    }

    fun removeUserDisplayItem(item: UserDisplayItem) = viewModelScope.launch {
        val position = userDisplays.indexOfFirst { it.id == item.id }
        userDisplayRepository.removeUserDisplay(item.toEntity())
        userDisplays.removeAt(position)
        sendEvent(UserDisplayEvent.ItemRemoved(item, position))
    }

    fun updateUserDisplays(userDisplayItems: List<UserDisplayItem>) = viewModelScope.launch {
        val entries = userDisplayItems.map(UserDisplayItem::toEntity)
        userDisplayRepository.updateUserDisplays(entries)
    }

    private suspend fun sendEvent(event: UserDisplayEvent) = withContext(Dispatchers.Main.immediate) {
        eventChannel.send(event)
    }
}
