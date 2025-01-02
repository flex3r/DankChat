package com.flxrs.dankchat.preferences.appearance

import kotlinx.serialization.Serializable

@Serializable
data class AppearanceSettings(
    val theme: ThemePreference = ThemePreference.System,
    val trueDarkTheme: Boolean = false,
    val fontSize: Int = 14,
    val keepScreenOn: Boolean = true,
    val lineSeparator: Boolean = false,
    val checkeredMessages: Boolean = false,
    val showInput: Boolean = true,
    val autoDisableInput: Boolean = true,
    val showChips: Boolean = true,
    val showChangelogs: Boolean = true,
)

enum class ThemePreference { System, Dark, Light }
