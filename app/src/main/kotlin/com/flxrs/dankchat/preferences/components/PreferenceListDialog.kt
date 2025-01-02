package com.flxrs.dankchat.preferences.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Enum<*>> PreferenceListDialog(
    values: ImmutableList<T>,
    entries: ImmutableList<String>,
    selected: T,
    onChanged: (T) -> Unit,
    onDismissRequested: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequested,
    ) {
        values.forEachIndexed { idx, it ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected == it, onClick = { onChanged(it) })
                    .padding(horizontal = 16.dp),
            ) {
                RadioButton(selected = selected == it, onClick = { onChanged(it) })
                Text(
                    text = entries[idx],
                    modifier = Modifier.padding(start = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 18.sp,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
