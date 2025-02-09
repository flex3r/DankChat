package com.flxrs.dankchat.preferences.stream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class StreamsSettingsViewModel(
    private val dataStore: StreamsSettingsDataStore,
) : ViewModel() {

    val settings = dataStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5.seconds),
        initialValue = dataStore.current(),
    )

    fun onInteraction(interaction: StreamsSettingsInteraction) = viewModelScope.launch {
        runCatching {
            when (interaction) {
                is StreamsSettingsInteraction.FetchStreams         -> dataStore.update { it.copy(fetchStreams = interaction.value) }
                is StreamsSettingsInteraction.ShowStreamInfo       -> dataStore.update { it.copy(showStreamInfo = interaction.value) }
                is StreamsSettingsInteraction.PreventStreamReloads -> dataStore.update { it.copy(preventStreamReloads = interaction.value) }
                is StreamsSettingsInteraction.EnablePiP            -> dataStore.update { it.copy(enablePiP = interaction.value) }
            }
        }
    }
}

sealed interface StreamsSettingsInteraction {
    data class FetchStreams(val value: Boolean) : StreamsSettingsInteraction
    data class ShowStreamInfo(val value: Boolean) : StreamsSettingsInteraction
    data class PreventStreamReloads(val value: Boolean) : StreamsSettingsInteraction
    data class EnablePiP(val value: Boolean) : StreamsSettingsInteraction
}
