package com.flxrs.dankchat.preferences.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.withTrailingSlash
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class DeveloperSettingsViewModel(
    private val developerSettingsDataStore: DeveloperSettingsDataStore,
    private val dankchatPreferenceStore: DankChatPreferenceStore,
) : ViewModel() {

    val settings = developerSettingsDataStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5.seconds),
        initialValue = developerSettingsDataStore.current(),
    )

    private val _events = MutableSharedFlow<DeveloperSettingsEvents>()
    val events = _events.asSharedFlow()

    fun onInteraction(interaction: DeveloperSettingsInteraction) = viewModelScope.launch {
        runCatching {
            when (interaction) {
                is DeveloperSettingsInteraction.DebugMode                  -> developerSettingsDataStore.update { it.copy(debugMode = interaction.value) }
                is DeveloperSettingsInteraction.RepeatedSending            -> developerSettingsDataStore.update { it.copy(repeatedSending = interaction.value) }
                is DeveloperSettingsInteraction.BypassCommandHandling      -> developerSettingsDataStore.update { it.copy(bypassCommandHandling = interaction.value) }
                is DeveloperSettingsInteraction.CustomRecentMessagesHost   -> {
                    val withSlash = interaction.host
                        .ifBlank { DeveloperSettings.RM_HOST_DEFAULT }
                        .withTrailingSlash
                    if (withSlash == developerSettingsDataStore.current().customRecentMessagesHost) return@launch
                    developerSettingsDataStore.update { it.copy(customRecentMessagesHost = withSlash) }
                    _events.emit(DeveloperSettingsEvents.RestartRequired)
                }
                is DeveloperSettingsInteraction.RestartRequired -> _events.emit(DeveloperSettingsEvents.RestartRequired)
            }
        }
    }
}

sealed interface DeveloperSettingsEvents {
    data object RestartRequired : DeveloperSettingsEvents
}

sealed interface DeveloperSettingsInteraction {
    data class DebugMode(val value: Boolean) : DeveloperSettingsInteraction
    data class RepeatedSending(val value: Boolean) : DeveloperSettingsInteraction
    data class BypassCommandHandling(val value: Boolean) : DeveloperSettingsInteraction
    data class CustomRecentMessagesHost(val host: String) : DeveloperSettingsInteraction
    data object RestartRequired : DeveloperSettingsInteraction
}


