package com.flxrs.dankchat.ui.main

import kotlinx.serialization.Serializable

@Serializable
object Main

@Serializable
object Settings

@Serializable
object AppearanceSettings

@Serializable
object NotificationsSettings

@Serializable
object HighlightsSettings

@Serializable
object IgnoresSettings

@Serializable
object ChatSettings

@Serializable
object CustomCommandsSettings

@Serializable
object UserDisplaySettings

@Serializable
object DankChatAccountSettings

@Serializable
object StreamsSettings

@Serializable
object BatterySettings

@Serializable
object ToolsSettings

@Serializable
object ImageUploaderSettings

@Serializable
object TTSUserIgnoreListSettings

@Serializable
object DeveloperSettings

@Serializable
object ChangelogSettings

@Serializable
object AboutSettings

@Serializable
object EmoteMenu

@Serializable
object Login

@Serializable
object Onboarding

@Serializable
data class LogViewer(
    val fileName: String = "",
)

@Serializable
data class CrashViewer(
    val crashId: Long = 0L,
)
