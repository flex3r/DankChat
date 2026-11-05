package com.flxrs.dankchat.ui.main.input

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.appearance.InputAction
import com.flxrs.dankchat.utils.compose.rememberModalSheetState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import sh.calvin.reorderable.ReorderableColumn

private const val MAX_INPUT_ACTIONS = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InputActionConfigSheet(
    inputActions: ImmutableList<InputAction>,
    debugMode: Boolean,
    onInputActionsChange: (ImmutableList<InputAction>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalSheetState()

    val localEnabled = remember { mutableStateListOf(*inputActions.toTypedArray()) }

    val disabledActions = InputAction.entries.filter { it !in localEnabled && (it != InputAction.Debug || debugMode) }
    val atLimit = localEnabled.size >= MAX_INPUT_ACTIONS

    ModalBottomSheet(
        onDismissRequest = {
            onInputActionsChange(localEnabled.toImmutableList())
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        ) {
            val headerText = when {
                localEnabled.isEmpty() -> stringResource(R.string.input_actions_none)
                atLimit -> pluralStringResource(R.plurals.input_actions_max, MAX_INPUT_ACTIONS, MAX_INPUT_ACTIONS)
                else -> ""
            }
            Text(
                text = headerText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Enabled actions (reorderable, drag constrained to this section)
            ReorderableColumn(
                list = localEnabled.toList(),
                onSettle = { from, to ->
                    localEnabled.apply { add(to, removeAt(from)) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { _, action, isDragging ->
                key(action) {
                    ReorderableItem {
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)

                        Surface(
                            shadowElevation = elevation,
                            color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .longPressDraggableHandle()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .height(40.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = stringResource(action.labelRes),
                                    modifier = Modifier.weight(1f),
                                )
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = { localEnabled.remove(action) },
                                )
                            }
                        }
                    }
                }
            }

            // Divider between enabled and disabled
            if (disabledActions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }

            // Disabled actions (not reorderable)
            for (action in disabledActions) {
                val actionEnabled = !atLimit

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (actionEnabled) {
                                    Modifier.clickable { localEnabled.add(action) }
                                } else {
                                    Modifier
                                },
                            ).padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint =
                            if (actionEnabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            },
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(action.labelRes),
                        modifier = Modifier.weight(1f),
                        color =
                            if (actionEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                    )
                    Checkbox(
                        checked = false,
                        onCheckedChange = { localEnabled.add(action) },
                        enabled = actionEnabled,
                    )
                }
            }
        }
    }
}

internal val InputAction.labelRes: Int
    get() =
        when (this) {
            InputAction.Search -> R.string.input_action_search
            InputAction.LastMessage -> R.string.input_action_last_message
            InputAction.Stream -> R.string.input_action_stream
            InputAction.ModActions -> R.string.input_action_mod_actions
            InputAction.Fullscreen -> R.string.input_action_fullscreen
            InputAction.Theater -> R.string.input_action_theater
            InputAction.HideInput -> R.string.input_action_hide_input
            InputAction.TTS -> R.string.input_action_tts
            InputAction.Debug -> R.string.input_action_debug
        }

internal val InputAction.icon: ImageVector
    get() =
        when (this) {
            InputAction.Search -> Icons.Default.Search
            InputAction.LastMessage -> Icons.Default.History
            InputAction.Stream -> Icons.Outlined.Videocam
            InputAction.ModActions -> Icons.Outlined.Shield
            InputAction.Fullscreen -> Icons.Default.Fullscreen
            InputAction.Theater -> Icons.Default.Theaters
            InputAction.HideInput -> Icons.Default.VisibilityOff
            InputAction.TTS -> Icons.Default.RecordVoiceOver
            InputAction.Debug -> Icons.Default.BugReport
        }
