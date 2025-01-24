package com.flxrs.dankchat.preferences.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class ToolsSettingsViewModel(
    private val toolsSettingsDataStore: ToolsSettingsDataStore,
) : ViewModel() {

    val settings = toolsSettingsDataStore.settings
        .map { it.toState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds),
            initialValue = toolsSettingsDataStore.current().toState(),
        )

    fun onInteraction(interaction: ToolsSettingsInteraction) = viewModelScope.launch {
        runCatching {
            when (interaction) {
                is ToolsSettingsInteraction.TTSEnabled        -> toolsSettingsDataStore.update { it.copy(ttsEnabled = interaction.value) }
                is ToolsSettingsInteraction.TTSMode     -> toolsSettingsDataStore.update { it.copy(ttsPlayMode = interaction.value) }
                is ToolsSettingsInteraction.TTSFormat       -> toolsSettingsDataStore.update { it.copy(ttsMessageFormat = interaction.value) }
                is ToolsSettingsInteraction.TTSForceEnglish -> toolsSettingsDataStore.update { it.copy(ttsForceEnglish = interaction.value) }
                is ToolsSettingsInteraction.TTSIgnoreUrls     -> toolsSettingsDataStore.update { it.copy(ttsIgnoreUrls = interaction.value) }
                is ToolsSettingsInteraction.TTSIgnoreEmotes   -> toolsSettingsDataStore.update { it.copy(ttsIgnoreEmotes = interaction.value) }
                is ToolsSettingsInteraction.TTSUserIgnoreList -> toolsSettingsDataStore.update { it.copy(ttsUserIgnoreList = interaction.value) }
            }
        }
    }
}

sealed interface ToolsSettingsInteraction {
    data class TTSEnabled(val value: Boolean) : ToolsSettingsInteraction
    data class TTSMode(val value: TTSPlayMode) : ToolsSettingsInteraction
    data class TTSFormat(val value: TTSMessageFormat) : ToolsSettingsInteraction
    data class TTSForceEnglish(val value: Boolean) : ToolsSettingsInteraction
    data class TTSIgnoreUrls(val value: Boolean) : ToolsSettingsInteraction
    data class TTSIgnoreEmotes(val value: Boolean) : ToolsSettingsInteraction
    data class TTSUserIgnoreList(val value: Set<String>) : ToolsSettingsInteraction
}

data class ToolsSettingsState(
    val imageUploader: ImageUploaderConfig,
    val ttsEnabled: Boolean,
    val ttsPlayMode: TTSPlayMode,
    val ttsMessageFormat: TTSMessageFormat,
    val ttsForceEnglish: Boolean,
    val ttsIgnoreUrls: Boolean,
    val ttsIgnoreEmotes: Boolean,
    val ttsUserIgnoreList: ImmutableSet<String>
)

private fun ToolsSettings.toState() = ToolsSettingsState(
    imageUploader = uploaderConfig,
    ttsEnabled = ttsEnabled,
    ttsPlayMode = ttsPlayMode,
    ttsMessageFormat = ttsMessageFormat,
    ttsForceEnglish = ttsForceEnglish,
    ttsIgnoreUrls = ttsIgnoreUrls,
    ttsIgnoreEmotes = ttsIgnoreEmotes,
    ttsUserIgnoreList = ttsUserIgnoreList.toImmutableSet(),
)
