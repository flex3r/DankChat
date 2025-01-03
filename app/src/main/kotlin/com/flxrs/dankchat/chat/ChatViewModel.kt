package com.flxrs.dankchat.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.chat.ChatViewModel.ChatFragmentSettings.Companion.fromSettings
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.preferences.appearance.AppearanceSettings
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    repository: ChatRepository,
    appearanceSettingsDataStore: AppearanceSettingsDataStore
) : ViewModel() {
    private val args = ChatFragmentArgs.fromSavedStateHandle(savedStateHandle)
    val chat: StateFlow<List<ChatItem>> = repository
        .getChat(args.channel)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), emptyList())

    val settings = appearanceSettingsDataStore
        .settings
        .map { ChatFragmentSettings(it.lineSeparator) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), fromSettings(appearanceSettingsDataStore.current()))

    data class ChatFragmentSettings(val lineSeparator: Boolean) {
        companion object {
            fun fromSettings(appearanceSettings: AppearanceSettings) = ChatFragmentSettings(appearanceSettings.lineSeparator)
        }
    }

    companion object {
        private val TAG = ChatViewModel::class.java.simpleName
    }
}

