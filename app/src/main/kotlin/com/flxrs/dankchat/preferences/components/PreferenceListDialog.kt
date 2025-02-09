package com.flxrs.dankchat.preferences.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

@Composable
fun <T> PreferenceListDialog(
    title: String,
    values: ImmutableList<T>,
    entries: ImmutableList<String>,
    selected: T,
    onChanged: (T) -> Unit,
    isEnabled: Boolean = true,
    summary: String? = null,
    icon: ImageVector? = null,
) {
    val scope = rememberCoroutineScope()
    ExpandablePreferenceItem(
        title = title,
        summary = summary,
        icon = icon,
        isEnabled = isEnabled,
    ) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = ::dismiss,
            sheetState = sheetState,
        ) {
            values.forEachIndexed { idx, it ->
                val interactionSource = remember { MutableInteractionSource() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected == it,
                            onClick = {
                                onChanged(it)
                                scope.launch {
                                    sheetState.hide()
                                    dismiss()
                                }
                            },
                            interactionSource = interactionSource,
                            indication = ripple(),
                        )
                        .padding(horizontal = 16.dp),
                ) {
                    RadioButton(
                        selected = selected == it,
                        onClick = {
                            onChanged(it)
                            scope.launch {
                                sheetState.hide()
                                dismiss()
                            }
                        },
                        interactionSource = interactionSource,
                    )
                    Text(
                        text = entries[idx],
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 18.sp,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
