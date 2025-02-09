package com.flxrs.dankchat.preferences.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.RecentUploadsRepository
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class ToolsSettingsViewModel(
    private val toolsSettingsDataStore: ToolsSettingsDataStore,
    private val recentUploadsRepository: RecentUploadsRepository,
) : ViewModel() {

    val settings = combine(
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
                is ToolsSettingsInteraction.TTSEnabled        -> toolsSettingsDataStore.update { it.copy(ttsEnabled = interaction.value) }
                is ToolsSettingsInteraction.TTSMode           -> toolsSettingsDataStore.update { it.copy(ttsPlayMode = interaction.value) }
                is ToolsSettingsInteraction.TTSFormat         -> toolsSettingsDataStore.update { it.copy(ttsMessageFormat = interaction.value) }
                is ToolsSettingsInteraction.TTSForceEnglish   -> toolsSettingsDataStore.update { it.copy(ttsForceEnglish = interaction.value) }
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
    val hasRecentUploads: Boolean,
    val ttsEnabled: Boolean,
    val ttsPlayMode: TTSPlayMode,
    val ttsMessageFormat: TTSMessageFormat,
    val ttsForceEnglish: Boolean,
    val ttsIgnoreUrls: Boolean,
    val ttsIgnoreEmotes: Boolean,
    val ttsUserIgnoreList: ImmutableSet<String>,
)

private fun ToolsSettings.toState(hasRecentUploads: Boolean) = ToolsSettingsState(
    imageUploader = uploaderConfig,
    hasRecentUploads = hasRecentUploads,
    ttsEnabled = ttsEnabled,
    ttsPlayMode = ttsPlayMode,
    ttsMessageFormat = ttsMessageFormat,
    ttsForceEnglish = ttsForceEnglish,
    ttsIgnoreUrls = ttsIgnoreUrls,
    ttsIgnoreEmotes = ttsIgnoreEmotes,
    ttsUserIgnoreList = ttsUserIgnoreList.toImmutableSet(),
)
