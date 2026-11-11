package com.flxrs.dankchat.ui.main.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.ui.main.input.ChatInputViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ModActionsSheetContainer() {
    val modActionsViewModel: ModActionsViewModel = koinViewModel()
    val chatInputViewModel: ChatInputViewModel = koinViewModel()

    val uiState by modActionsViewModel.state.collectAsStateWithLifecycle()
    val currentState = uiState ?: return

    ModActionsDialog(
        roomState = currentState.roomState,
        isBroadcaster = currentState.isBroadcaster,
        isLive = currentState.isLive,
        shieldModeActive = currentState.shieldModeActive,
        onSendCommand = chatInputViewModel::trySendMessageOrCommand,
        onAnnounce = { chatInputViewModel.setAnnouncing(true) },
        onDismiss = modActionsViewModel::dismiss,
    )
}
