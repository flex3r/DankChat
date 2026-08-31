package com.flxrs.dankchat.preferences.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class AppearanceSettingsViewModel(
    private val dataStore: AppearanceSettingsDataStore,
) : ViewModel() {
    val settings =
        dataStore.settings
            .map { AppearanceSettingsUiState(settings = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5.seconds),
                initialValue = AppearanceSettingsUiState(settings = dataStore.current()),
            )

    init {
        viewModelScope.launch {
            val current = dataStore.current()
            if (current.accentColor != null && current.paletteStyle == PaletteStylePreference.SystemDefault) {
                dataStore.update { it.copy(paletteStyle = PaletteStylePreference.TonalSpot) }
            }
        }
    }

    suspend fun onSuspendingInteraction(interaction: AppearanceSettingsInteraction) {
        runCatching {
            when (interaction) {
                is AppearanceSettingsInteraction.Theme -> dataStore.update { it.copy(theme = interaction.theme) }

                is AppearanceSettingsInteraction.TrueDarkTheme -> dataStore.update { it.copy(trueDarkTheme = interaction.trueDarkTheme) }

                is AppearanceSettingsInteraction.FontSize -> dataStore.update { it.copy(fontSize = interaction.fontSize) }

                is AppearanceSettingsInteraction.KeepScreenOn -> dataStore.update { it.copy(keepScreenOn = interaction.value) }

                is AppearanceSettingsInteraction.LineSeparator -> dataStore.update { it.copy(lineSeparator = interaction.value) }

                is AppearanceSettingsInteraction.CheckeredMessages -> dataStore.update { it.copy(checkeredMessages = interaction.value) }

                is AppearanceSettingsInteraction.AutoDisableInput -> dataStore.update { it.copy(autoDisableInput = interaction.value) }

                is AppearanceSettingsInteraction.ShowChangelogs -> dataStore.update { it.copy(showChangelogs = interaction.value) }

                is AppearanceSettingsInteraction.ShowCharacterCounter -> dataStore.update { it.copy(showCharacterCounter = interaction.value) }

                is AppearanceSettingsInteraction.ShowSendWaitTimer -> dataStore.update { it.copy(showSendWaitTimer = interaction.value) }

                is AppearanceSettingsInteraction.ShowClearInputButton -> dataStore.update { it.copy(showClearInputButton = interaction.value) }

                is AppearanceSettingsInteraction.ShowSendButton -> dataStore.update { it.copy(showSendButton = interaction.value) }

                is AppearanceSettingsInteraction.SwipeNavigation -> dataStore.update { it.copy(swipeNavigation = interaction.value) }

                is AppearanceSettingsInteraction.SetAccentColor -> dataStore.update { settings ->
                    val adjustedPaletteStyle = when {
                        interaction.color != null && settings.paletteStyle == PaletteStylePreference.SystemDefault -> PaletteStylePreference.TonalSpot
                        interaction.color == null && settings.paletteStyle == PaletteStylePreference.TonalSpot -> PaletteStylePreference.SystemDefault
                        else -> settings.paletteStyle
                    }
                    settings.copy(accentColor = interaction.color, paletteStyle = adjustedPaletteStyle)
                }

                is AppearanceSettingsInteraction.SetPaletteStyle -> dataStore.update { it.copy(paletteStyle = interaction.style) }

                is AppearanceSettingsInteraction.FullscreenButtonOpacity -> dataStore.update { it.copy(fullscreenButtonOpacity = interaction.value) }

                is AppearanceSettingsInteraction.RequireFullscreenExitConfirmation -> dataStore.update { it.copy(requireFullscreenExitConfirmation = interaction.value) }

                is AppearanceSettingsInteraction.CompactChannelInfo -> dataStore.update { it.copy(compactChannelInfo = interaction.value) }
            }
        }
    }

    fun onInteraction(interaction: AppearanceSettingsInteraction) = viewModelScope.launch { onSuspendingInteraction(interaction) }
}
