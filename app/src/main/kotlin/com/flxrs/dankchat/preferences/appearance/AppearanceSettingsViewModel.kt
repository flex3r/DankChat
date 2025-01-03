package com.flxrs.dankchat.preferences.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class AppearanceSettingsViewModel(
    private val dataStore: AppearanceSettingsDataStore,
) : ViewModel() {

    val settings = dataStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5.seconds),
        initialValue = dataStore.current(),
    )

    suspend fun onSuspendingInteraction(interaction: AppearanceSettingsInteraction) {
        runCatching {
            when (interaction) {
                is AppearanceSettingsInteraction.Theme             -> dataStore.update { it.copy(theme = interaction.theme) }
                is AppearanceSettingsInteraction.TrueDarkTheme     -> dataStore.update { it.copy(trueDarkTheme = interaction.trueDarkTheme) }
                is AppearanceSettingsInteraction.FontSize          -> dataStore.update { it.copy(fontSize = interaction.fontSize) }
                is AppearanceSettingsInteraction.KeepScreenOn      -> dataStore.update { it.copy(keepScreenOn = interaction.value) }
                is AppearanceSettingsInteraction.LineSeparator     -> dataStore.update { it.copy(lineSeparator = interaction.value) }
                is AppearanceSettingsInteraction.CheckeredMessages -> dataStore.update { it.copy(checkeredMessages = interaction.value) }
                is AppearanceSettingsInteraction.ShowInput         -> dataStore.update { it.copy(showInput = interaction.value) }
                is AppearanceSettingsInteraction.AutoDisableInput  -> dataStore.update { it.copy(autoDisableInput = interaction.value) }
                is AppearanceSettingsInteraction.ShowChips         -> dataStore.update { it.copy(showChips = interaction.value) }
                is AppearanceSettingsInteraction.ShowChangelogs    -> dataStore.update { it.copy(showChangelogs = interaction.value) }
            }
        }
    }

    fun onInteraction(interaction: AppearanceSettingsInteraction) = viewModelScope.launch { onSuspendingInteraction(interaction) }
}

sealed interface AppearanceSettingsInteraction {
    data class Theme(val theme: ThemePreference) : AppearanceSettingsInteraction
    data class TrueDarkTheme(val trueDarkTheme: Boolean) : AppearanceSettingsInteraction
    data class FontSize(val fontSize: Int) : AppearanceSettingsInteraction
    data class KeepScreenOn(val value: Boolean) : AppearanceSettingsInteraction
    data class LineSeparator(val value: Boolean) : AppearanceSettingsInteraction
    data class CheckeredMessages(val value: Boolean) : AppearanceSettingsInteraction
    data class ShowInput(val value: Boolean) : AppearanceSettingsInteraction
    data class AutoDisableInput(val value: Boolean) : AppearanceSettingsInteraction
    data class ShowChips(val value: Boolean) : AppearanceSettingsInteraction
    data class ShowChangelogs(val value: Boolean) : AppearanceSettingsInteraction
}
