package com.flxrs.dankchat.preferences.ui.userdisplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.database.entity.UserDisplayEntity
import com.flxrs.dankchat.data.repo.UserDisplayRepository
import com.flxrs.dankchat.data.twitch.message.Message
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class UserDisplayViewModel(
    private val userDisplayRepository: UserDisplayRepository
) : ViewModel() {

    private val eventChannel = Channel<UserDisplayEvent>(Channel.CONFLATED)

    val events = eventChannel.receiveAsFlow()

    val userDisplays = userDisplayRepository.userDisplays.map { userDisplays ->
        val entries = userDisplays
            .map(UserDisplayEntity::toEntry)
            .sortedByDescending { it.id } // preserve order since new entries are added at the top of the list
        listOf(UserDisplayItem.AddEntry) + entries
    }

    private suspend fun newBlankEntry() {
        userDisplayRepository.addUserDisplay(
            UserDisplayItem.Entry(
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

    private suspend fun addEntry(entry: UserDisplayItem.Entry) {
        userDisplayRepository.addUserDisplay(entry.toEntity())
    }

    private suspend fun saveEntries(userDisplayEntries: List<UserDisplayItem>) {
        val entries = userDisplayEntries
            .filterIsInstance<UserDisplayItem.Entry>()
            .map(UserDisplayItem.Entry::toEntity)

        userDisplayRepository.addUserDisplays(entries)
    }

    fun saveChangesAndCreateNewBlank(userDisplayEntries: List<UserDisplayItem>) = viewModelScope.launch {
        saveEntries(userDisplayEntries)
        newBlankEntry()
    }

    fun saveChangesAndAddEntry(userDisplayEntries: List<UserDisplayItem>, entry: UserDisplayItem.Entry) = viewModelScope.launch {
        saveEntries(userDisplayEntries)
        addEntry(entry)
    }

    fun saveChanges(userDisplayEntries: List<UserDisplayItem>) = viewModelScope.launch {
        saveEntries(userDisplayEntries)
    }

    fun deleteEntry(userDisplayEntry: UserDisplayItem.Entry) = viewModelScope.launch {
        eventChannel.trySend(UserDisplayEvent.ItemRemoved(userDisplayEntry))
        userDisplayRepository.delete(userDisplayEntry.toEntity())
    }
}
