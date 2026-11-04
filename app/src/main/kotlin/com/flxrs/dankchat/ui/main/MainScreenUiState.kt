package com.flxrs.dankchat.ui.main

import androidx.compose.runtime.Immutable
import com.flxrs.dankchat.preferences.appearance.InputAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class MainScreenUiState(
    val isFullscreen: Boolean = false,
    val showInput: Boolean = true,
    val inputActions: ImmutableList<InputAction> = persistentListOf(),
    val showCharacterCounter: Boolean = false,
    val isRepeatedSendEnabled: Boolean = false,
    val debugMode: Boolean = false,
    val swipeNavigation: Boolean = true,
    val fontSize: Int = 14,
    val keepStreamWhileTyping: Boolean = false,
    val gestureToolbarHidden: Boolean = false,
) {
    val effectiveShowAppBar: Boolean get() = !gestureToolbarHidden
}
