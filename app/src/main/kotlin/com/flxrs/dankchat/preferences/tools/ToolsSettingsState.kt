package com.flxrs.dankchat.preferences.tools

import kotlinx.collections.immutable.ImmutableSet

sealed interface ToolsSettingsInteraction {
    data class TTSEnabled(
        val value: Boolean,
    ) : ToolsSettingsInteraction

    data class TTSMode(
        val value: TTSPlayMode,
    ) : ToolsSettingsInteraction

    data class TTSFormat(
        val value: TTSMessageFormat,
    ) : ToolsSettingsInteraction

    data class TTSForceEnglish(
        val value: Boolean,
    ) : ToolsSettingsInteraction

    data class TTSIgnoreUrls(
        val value: Boolean,
    ) : ToolsSettingsInteraction

    data class TTSIgnoreEmotes(
        val value: Boolean,
    ) : ToolsSettingsInteraction

    data class TTSIgnoreCommands(
        val value: Boolean,
    ) : ToolsSettingsInteraction

    data class TTSIgnoreUserNameNumbers(
        val value: Boolean,
    ) : ToolsSettingsInteraction

    data class TTSVolume(
        val value: Float,
    ) : ToolsSettingsInteraction

    data class TTSAudioDucking(
        val value: Boolean,
    ) : ToolsSettingsInteraction

    data class TTSUserIgnoreList(
        val value: Set<String>,
    ) : ToolsSettingsInteraction
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
    val ttsIgnoreCommands: Boolean,
    val ttsIgnoreUserNameNumbers: Boolean,
    val ttsVolume: Float,
    val ttsAudioDucking: Boolean,
    val ttsUserIgnoreList: ImmutableSet<String>,
)
