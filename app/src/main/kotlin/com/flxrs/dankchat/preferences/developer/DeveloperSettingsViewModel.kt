package com.flxrs.dankchat.preferences.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.withTrailingSlash
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class DeveloperSettingsViewModel(
    private val developerSettingsDataStore: DeveloperSettingsDataStore,
    private val dankchatPreferenceStore: DankChatPreferenceStore,
) : ViewModel() {

    private val initial = developerSettingsDataStore.current()
    val settings = developerSettingsDataStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5.seconds),
        initialValue = initial,
    )

    private val _events = MutableSharedFlow<DeveloperSettingsEvent>()
    val events = _events.asSharedFlow()

    fun onInteraction(interaction: DeveloperSettingsInteraction) = viewModelScope.launch {
        runCatching {
            when (interaction) {
                is DeveloperSettingsInteraction.DebugMode                -> developerSettingsDataStore.update { it.copy(debugMode = interaction.value) }
                is DeveloperSettingsInteraction.RepeatedSending          -> developerSettingsDataStore.update { it.copy(repeatedSending = interaction.value) }
                is DeveloperSettingsInteraction.BypassCommandHandling    -> developerSettingsDataStore.update { it.copy(bypassCommandHandling = interaction.value) }
                is DeveloperSettingsInteraction.CustomRecentMessagesHost -> {
                    val withSlash = interaction.host
                        .ifBlank { DeveloperSettings.RM_HOST_DEFAULT }
                        .withTrailingSlash
                    if (withSlash == developerSettingsDataStore.settings.first().customRecentMessagesHost) return@launch
                    developerSettingsDataStore.update { it.copy(customRecentMessagesHost = withSlash) }
                    _events.emit(DeveloperSettingsEvent.RestartRequired)
                }

                is DeveloperSettingsInteraction.EventSubEnabled          -> {
                    developerSettingsDataStore.update { it.copy(eventSubEnabled = interaction.value) }
                    if (initial.eventSubEnabled != interaction.value) {
                        _events.emit(DeveloperSettingsEvent.RestartRequired)
                    }
                }

                is DeveloperSettingsInteraction.EventSubDebugOutput      -> developerSettingsDataStore.update { it.copy(eventSubDebugOutput = interaction.value) }
                is DeveloperSettingsInteraction.RestartRequired          -> _events.emit(DeveloperSettingsEvent.RestartRequired)
            }
        }
    }
}

sealed interface DeveloperSettingsEvent {
    data object RestartRequired : DeveloperSettingsEvent
}

sealed interface DeveloperSettingsInteraction {
    data class DebugMode(val value: Boolean) : DeveloperSettingsInteraction
    data class RepeatedSending(val value: Boolean) : DeveloperSettingsInteraction
    data class BypassCommandHandling(val value: Boolean) : DeveloperSettingsInteraction
    data class CustomRecentMessagesHost(val host: String) : DeveloperSettingsInteraction
    data class EventSubEnabled(val value: Boolean) : DeveloperSettingsInteraction
    data class EventSubDebugOutput(val value: Boolean) : DeveloperSettingsInteraction
    data object RestartRequired : DeveloperSettingsInteraction
}


