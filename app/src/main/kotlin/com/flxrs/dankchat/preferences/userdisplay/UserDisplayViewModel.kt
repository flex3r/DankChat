package com.flxrs.dankchat.preferences.userdisplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.UserDisplayRepository
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayItem.AddEntry.toEntity
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayItem.AddEntry.toEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserDisplayViewModel @Inject constructor(
    private val userDisplayRepository: UserDisplayRepository
) : ViewModel() {
    private val eventChannel = Channel<UserDisplayEvent>(Channel.CONFLATED)

    val events = eventChannel.receiveAsFlow()

    val userDisplays = userDisplayRepository.userDisplays.map { entries ->
        listOf(UserDisplayItem.AddEntry) + entries.map { it.toEntry() }.sortedByDescending { it.id } // preserve order: since new entries is added at the top of the list
    }

    private suspend fun newBlankEntry() {
        userDisplayRepository.addUserDisplay(
            UserDisplayItem.Entry(
                id = 0, username = "", enabled = true, aliasEnabled = false, alias = "", colorEnabled = false, color = 0xff000000.toInt()
            ).toEntity()
        )
    }

    private suspend fun addEntry(entry: UserDisplayItem.Entry) {
        userDisplayRepository.addUserDisplay(entry.toEntity())
    }

    private suspend fun saveEntries(userDisplayEntries: List<UserDisplayItem>) {
        userDisplayRepository.addUserDisplays(userDisplayEntries.filterIsInstance<UserDisplayItem.Entry>().map { it.toEntity() })
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