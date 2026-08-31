package com.flxrs.dankchat.ui.main

import androidx.compose.runtime.Immutable

@Immutable
sealed interface ToolbarAction {
    data class SelectTab(
        val index: Int,
    ) : ToolbarAction

    data object LongClickTab : ToolbarAction

    data object AddChannel : ToolbarAction

    data object OpenMentions : ToolbarAction

    data object Login : ToolbarAction

    data object Relogin : ToolbarAction

    data object Logout : ToolbarAction

    data object ManageChannels : ToolbarAction

    data object OpenChannel : ToolbarAction

    data object RemoveChannel : ToolbarAction

    data object ReportChannel : ToolbarAction

    data object BlockChannel : ToolbarAction

    data object ToggleChannelNotifications : ToolbarAction

    data object CaptureImage : ToolbarAction

    data object CaptureVideo : ToolbarAction

    data object ChooseMedia : ToolbarAction

    data object ReloadEmotes : ToolbarAction

    data object Reconnect : ToolbarAction

    data object OpenSettings : ToolbarAction

    data object TogglePinnedMessage : ToolbarAction
}
