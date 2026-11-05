package com.flxrs.dankchat.preferences.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.RecentUploadsRepository
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class ToolsSettingsViewModel(
    private val toolsSettingsDataStore: ToolsSettingsDataStore,
    recentUploadsRepository: RecentUploadsRepository,
) : ViewModel() {
    val settings =
        combine(
            toolsSettingsDataStore.settings,
            recentUploadsRepository.getRecentUploads(),
        ) { toolsSettings, recentUploads ->
            toolsSettings.toState(hasRecentUploads = recentUploads.isNotEmpty())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds),
            initialValue = toolsSettingsDataStore.current().toState(hasRecentUploads = false),
        )

    fun onInteraction(interaction: ToolsSettingsInteraction) = viewModelScope.launch {
        runCatching {
            when (interaction) {
                is ToolsSettingsInteraction.TTSEnabled -> toolsSettingsDataStore.update { it.copy(ttsEnabled = interaction.value) }
                is ToolsSettingsInteraction.TTSMode -> toolsSettingsDataStore.update { it.copy(ttsPlayMode = interaction.value) }
                is ToolsSettingsInteraction.TTSFormat -> toolsSettingsDataStore.update { it.copy(ttsMessageFormat = interaction.value) }
                is ToolsSettingsInteraction.TTSForceEnglish -> toolsSettingsDataStore.update { it.copy(ttsForceEnglish = interaction.value) }
                is ToolsSettingsInteraction.TTSIgnoreUrls -> toolsSettingsDataStore.update { it.copy(ttsIgnoreUrls = interaction.value) }
                is ToolsSettingsInteraction.TTSIgnoreEmotes -> toolsSettingsDataStore.update { it.copy(ttsIgnoreEmotes = interaction.value) }
                is ToolsSettingsInteraction.TTSIgnoreCommands -> toolsSettingsDataStore.update { it.copy(ttsIgnoreCommands = interaction.value) }
                is ToolsSettingsInteraction.TTSIgnoreUserNameNumbers -> toolsSettingsDataStore.update { it.copy(ttsIgnoreUserNameNumbers = interaction.value) }
                is ToolsSettingsInteraction.TTSVolume -> toolsSettingsDataStore.update { it.copy(ttsVolume = interaction.value) }
                is ToolsSettingsInteraction.TTSAudioDucking -> toolsSettingsDataStore.update { it.copy(ttsAudioDucking = interaction.value) }
                is ToolsSettingsInteraction.TTSUserIgnoreList -> toolsSettingsDataStore.update { it.copy(ttsUserIgnoreList = interaction.value) }
            }
        }
    }
}

private fun ToolsSettings.toState(hasRecentUploads: Boolean) = ToolsSettingsState(
    imageUploader = uploaderConfig,
    hasRecentUploads = hasRecentUploads,
    ttsEnabled = ttsEnabled,
    ttsPlayMode = ttsPlayMode,
    ttsMessageFormat = ttsMessageFormat,
    ttsForceEnglish = ttsForceEnglish,
    ttsIgnoreUrls = ttsIgnoreUrls,
    ttsIgnoreEmotes = ttsIgnoreEmotes,
    ttsIgnoreCommands = ttsIgnoreCommands,
    ttsIgnoreUserNameNumbers = ttsIgnoreUserNameNumbers,
    ttsVolume = ttsVolume,
    ttsAudioDucking = ttsAudioDucking,
    ttsUserIgnoreList = ttsUserIgnoreList.toImmutableSet(),
)
