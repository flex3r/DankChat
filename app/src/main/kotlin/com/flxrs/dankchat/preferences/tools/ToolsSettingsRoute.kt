package com.flxrs.dankchat.preferences.tools

import kotlinx.serialization.Serializable

sealed interface ToolsSettingsRoute {
    @Serializable
    data object ToolsSettings : ToolsSettingsRoute

    @Serializable
    data object ImageUploader : ToolsSettingsRoute

    @Serializable
    data object TTSUserIgnoreList : ToolsSettingsRoute
}
