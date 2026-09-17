package com.flxrs.dankchat.ui.main.input

import com.flxrs.dankchat.preferences.appearance.InputAction
import kotlinx.collections.immutable.ImmutableList

data class ChatInputCallbacks(
    val onSend: () -> Unit,
    val onLastMessageClick: () -> Unit,
    val onEmoteClick: () -> Unit,
    val onOverlayDismiss: () -> Unit,
    val onToggleFullscreen: () -> Unit,
    val onToggleInput: () -> Unit,
    val onToggleStream: () -> Unit,
    val onAudioOnly: () -> Unit,
    val onToggleTheater: () -> Unit,
    val onModActions: () -> Unit,
    val onInputActionsChange: (ImmutableList<InputAction>) -> Unit,
    val onRecentMessageClick: (String) -> Unit = {},
    val onSearchClick: () -> Unit = {},
    val onDebugInfoClick: () -> Unit = {},
    val onNewWhisper: (() -> Unit)? = null,
    val onRepeatedSendChange: (Boolean) -> Unit = {},
    val onInputScrollableChanged: (Boolean) -> Unit = {},
)
