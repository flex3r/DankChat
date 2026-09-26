package com.flxrs.dankchat.preferences.appearance

import androidx.compose.runtime.Immutable

sealed interface AppearanceSettingsInteraction {
    data class Theme(
        val theme: ThemePreference,
    ) : AppearanceSettingsInteraction

    data class TrueDarkTheme(
        val trueDarkTheme: Boolean,
    ) : AppearanceSettingsInteraction

    data class FontSize(
        val fontSize: Int,
    ) : AppearanceSettingsInteraction

    data class KeepScreenOn(
        val value: Boolean,
    ) : AppearanceSettingsInteraction

    data class LineSeparator(
        val value: Boolean,
    ) : AppearanceSettingsInteraction

    data class CheckeredMessages(
        val value: Boolean,
    ) : AppearanceSettingsInteraction

    data class AutoDisableInput(
        val value: Boolean,
    ) : AppearanceSettingsInteraction

    data class ShowChangelogs(
        val value: Boolean,
    ) : AppearanceSettingsInteraction

    data class ShowCharacterCounter(
        val value: Boolean,
    ) : AppearanceSettingsInteraction

    data class ShowSendWaitTimer(
        val value: Boolean,
    ) : AppearanceSettingsInteraction

    data class ShowClearInputButton(
        val value: Boolean,
    ) : AppearanceSettingsInteraction

    data class ShowSendButton(
        val value: Boolean,
    ) : AppearanceSettingsInteraction

    data class SwipeNavigation(
        val value: Boolean,
    ) : AppearanceSettingsInteraction

    data class SetAccentColor(
        val color: AccentColor?,
    ) : AppearanceSettingsInteraction

    data class SetPaletteStyle(
        val style: PaletteStylePreference,
    ) : AppearanceSettingsInteraction

    data class FullscreenButtonOpacity(
        val value: Float,
    ) : AppearanceSettingsInteraction

    data class RequireFullscreenExitConfirmation(
        val value: Boolean,
    ) : AppearanceSettingsInteraction

    data class CompactChannelInfo(
        val value: Boolean,
    ) : AppearanceSettingsInteraction
}

@Immutable
data class AppearanceSettingsUiState(
    val settings: AppearanceSettings,
)
