package com.flxrs.dankchat.preferences.userdisplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.UserDisplayRepository
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayItem.AddEntry.toEntity
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayItem.AddEntry.toEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserDisplayViewModel @Inject constructor(
    private val userDisplayRepository: UserDisplayRepository
) : ViewModel() {

    val userDisplays = userDisplayRepository.userDisplays.map { entries ->
        listOf(UserDisplayItem.AddEntry) + entries.map { it.toEntry() }.sortedByDescending { it.id } // preserve order: since new entries is added at the top of the list
    }

    fun newBlankEntry() = viewModelScope.launch {
        userDisplayRepository.addUserDisplay(
            UserDisplayItem.Entry(
                id = 0, username = "", enabled = true, aliasEnabled = false, alias = "", colorEnabled = false, color = 0xff000000.toInt()
            ).toEntity()
        )
    }

    fun saveEntries(userDisplayEntries: List<UserDisplayItem>) = viewModelScope.launch {
        userDisplayRepository.addUserDisplays(userDisplayEntries.filterIsInstance<UserDisplayItem.Entry>().map { it.toEntity() })
    }

    fun deleteEntry(userDisplayEntry: UserDisplayItem.Entry) = viewModelScope.launch {
        userDisplayRepository.delete(userDisplayEntry.toEntity())
    }

}