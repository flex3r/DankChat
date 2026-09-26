package com.flxrs.dankchat.utils.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.core.SheetDetent
import com.flxrs.dankchat.R
import com.composables.core.rememberModalBottomSheetState as rememberUnstyledSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoBottomSheet(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = stringResource(R.string.dialog_dismiss),
    dismissible: Boolean = true,
) {
    when {
        dismissible -> {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = rememberModalSheetState(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                InfoSheetContent(title, message, confirmText, dismissText, onConfirm, onDismiss)
            }
        }

        else -> {
            val sheetState =
                rememberUnstyledSheetState(
                    initialDetent = SheetDetent.FullyExpanded,
                    detents = listOf(SheetDetent.Hidden, SheetDetent.FullyExpanded),
                )
            SideEffect(sheetState.currentDetent) {
                if (sheetState.currentDetent == SheetDetent.Hidden) {
                    sheetState.jumpTo(SheetDetent.FullyExpanded)
                }
            }
            com.composables.core.ModalBottomSheet(state = sheetState) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                    )
                    Surface(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .navigationBarsPadding(),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .padding(vertical = 12.dp)
                                        .align(Alignment.CenterHorizontally)
                                        .size(width = 32.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                            )
                            InfoSheetContent(title, message, confirmText, dismissText, onConfirm, onDismiss)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoSheetContent(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        }
    }
}
