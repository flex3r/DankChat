package com.flxrs.dankchat.preferences.chat.userdisplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.database.entity.UserDisplayEntity
import com.flxrs.dankchat.data.repo.UserDisplayRepository
import com.flxrs.dankchat.data.twitch.message.Message
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class UserDisplayViewModel(
    private val userDisplayRepository: UserDisplayRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<UserDisplayEvent>()
    val events = _events.asSharedFlow()

    val userDisplays = userDisplayRepository.userDisplays.map { userDisplays ->
        userDisplays.map(UserDisplayEntity::toItem).toImmutableList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), persistentListOf())

    fun saveChangesAndCreateNew(userDisplayItems: List<UserDisplayItem>) = viewModelScope.launch {
        saveItems(userDisplayItems)
        newItem()
    }

    fun saveChangesAndAddItem(userDisplayItems: List<UserDisplayItem>, item: UserDisplayItem) = viewModelScope.launch {
        saveItems(userDisplayItems)
        addItem(item)
    }

    fun saveChanges(userDisplayItems: List<UserDisplayItem>) = viewModelScope.launch {
        saveItems(userDisplayItems)
    }

    fun removeItem(userDisplayItem: UserDisplayItem) = viewModelScope.launch {
        _events.emit(UserDisplayEvent.ItemRemoved(userDisplayItem))
        userDisplayRepository.delete(userDisplayItem.toEntity())
    }

    private suspend fun newItem() {
        userDisplayRepository.updateUserDisplay(
            UserDisplayItem(
                id = 0,
                username = "",
                enabled = true,
                aliasEnabled = false,
                alias = "",
                colorEnabled = false,
                color = Message.DEFAULT_COLOR,
            ).toEntity()
        )
    }

    private suspend fun addItem(entry: UserDisplayItem) {
        userDisplayRepository.updateUserDisplay(entry.toEntity())
    }

    private suspend fun saveItems(userDisplayEntries: List<UserDisplayItem>) {
        val entries = userDisplayEntries.map(UserDisplayItem::toEntity)
        userDisplayRepository.updateUserDisplays(entries)
    }
}
