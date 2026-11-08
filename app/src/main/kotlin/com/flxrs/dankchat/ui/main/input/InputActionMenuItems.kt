package com.flxrs.dankchat.ui.main.input

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.appearance.InputAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class InputActionsState(
    val isInputShown: Boolean,
    val enabled: Boolean,
    val hasLastMessage: Boolean,
    val isStreamActive: Boolean,
    val hasStreamData: Boolean,
    val isFullscreen: Boolean,
    val isTTSEnabled: Boolean,
    val isTheaterMode: Boolean,
    val isModerator: Boolean,
    val debugMode: Boolean,
)

@Immutable
data class InputActionMenuItem(
    val action: InputAction,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val enabled: Boolean,
)

/**
 * Menu entries for every available action not in [shownActions].
 * Theater comes last so it sits next to the audio only entry.
 */
fun InputActionsState.overflowMenuItems(shownActions: List<InputAction>): ImmutableList<InputActionMenuItem> = InputAction.entries
    .filterNot { it in shownActions }
    .sortedBy { it == InputAction.Theater }
    .mapNotNull { it.toMenuItem(this) }
    .toImmutableList()

/** Floating buttons for [pinnedActions], without the step the escape button already takes. */
fun InputActionsState.pinnedFabItems(pinnedActions: List<InputAction>): ImmutableList<InputActionMenuItem> = pinnedActions
    .filterNot { it.isEscapeStep(this) }
    .mapNotNull { it.toMenuItem(this) }
    .toImmutableList()

/** Floating menu entries for the actions not in [pinnedActions], without the step the escape button already takes. */
fun InputActionsState.fabMenuItems(pinnedActions: List<InputAction>): ImmutableList<InputActionMenuItem> = overflowMenuItems(pinnedActions)
    .filterNot { it.action.isEscapeStep(this) }
    .toImmutableList()

private fun InputAction.isEscapeStep(state: InputActionsState): Boolean = when (this) {
    InputAction.Fullscreen -> state.isFullscreen
    InputAction.HideInput -> !state.isFullscreen
    else -> false
}

private fun InputAction.toMenuItem(state: InputActionsState): InputActionMenuItem? {
    val (labelRes, icon) =
        when (this) {
            InputAction.Search -> {
                R.string.input_action_search to Icons.Default.Search
            }

            InputAction.LastMessage -> {
                when {
                    state.isInputShown -> R.string.input_action_last_message to Icons.Default.History
                    else -> return null
                }
            }

            InputAction.Stream -> {
                when {
                    state.isStreamActive -> R.string.menu_hide_stream to Icons.Outlined.VideocamOff
                    state.hasStreamData -> R.string.menu_show_stream to Icons.Outlined.Videocam
                    else -> return null
                }
            }

            InputAction.ModActions -> {
                when {
                    state.isModerator -> R.string.menu_mod_actions to Icons.Outlined.Shield
                    else -> return null
                }
            }

            InputAction.Fullscreen -> {
                when {
                    // Theater mode is already fullscreen, so toggling chat fullscreen makes no sense there
                    state.isTheaterMode -> return null

                    state.isFullscreen -> R.string.menu_exit_fullscreen to Icons.Default.FullscreenExit

                    else -> R.string.menu_fullscreen to Icons.Default.Fullscreen
                }
            }

            InputAction.Theater -> {
                when {
                    !state.isStreamActive -> return null
                    state.isTheaterMode -> R.string.menu_exit_theater_mode to Icons.Default.Theaters
                    else -> R.string.menu_theater_mode to Icons.Default.Theaters
                }
            }

            InputAction.HideInput -> {
                when {
                    state.isInputShown -> R.string.menu_hide_input to Icons.Default.VisibilityOff
                    else -> R.string.menu_show_input to Icons.Default.Visibility
                }
            }

            InputAction.TTS -> {
                when {
                    state.isTTSEnabled -> R.string.menu_disable_tts to Icons.Default.VoiceOverOff
                    else -> R.string.menu_enable_tts to Icons.Default.RecordVoiceOver
                }
            }

            InputAction.Debug -> {
                when {
                    state.debugMode -> R.string.input_action_debug to Icons.Default.BugReport
                    else -> return null
                }
            }
        }
    return InputActionMenuItem(action = this, labelRes = labelRes, icon = icon, enabled = isEnabled(state))
}

private fun InputAction.isEnabled(state: InputActionsState): Boolean = when (this) {
    InputAction.Search, InputAction.Fullscreen, InputAction.HideInput, InputAction.TTS, InputAction.Debug -> true
    InputAction.LastMessage -> state.enabled && state.hasLastMessage
    InputAction.Stream, InputAction.ModActions -> state.enabled
    InputAction.Theater -> state.enabled && state.isStreamActive
}
