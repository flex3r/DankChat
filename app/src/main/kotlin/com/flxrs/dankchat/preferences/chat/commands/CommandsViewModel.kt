package com.flxrs.dankchat.preferences.chat.commands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.chat.CustomCommand
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class CommandsViewModel(private val chatSettingsDataStore: ChatSettingsDataStore) : ViewModel() {
    val commands = chatSettingsDataStore.settings
        .map { it.customCommands.toImmutableList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds),
            initialValue = chatSettingsDataStore.current().customCommands.toImmutableList(),
        )

    fun save(commands: List<CustomCommand>) = viewModelScope.launch {
        val filtered = commands.filter { it.trigger.isNotBlank() && it.command.isNotBlank() }
        chatSettingsDataStore.update { it.copy(customCommands = filtered) }
    }
}
