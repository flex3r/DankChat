package com.flxrs.dankchat.ui.main

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

private const val DRAG_DISMISS_THRESHOLD_FRACTION = 0.5f
private const val SWIPE_ALPHA_FADE = 0.4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DismissibleSnackbar(data: SnackbarData) {
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { total -> total * DRAG_DISMISS_THRESHOLD_FRACTION },
    )
    SideEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.StartToEnd) {
            data.dismiss()
        }
    }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {},
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
    ) {
        Snackbar(
            snackbarData = data,
            modifier = Modifier.graphicsLayer {
                val width = size.width
                if (width > 0f) {
                    val offset = runCatching { state.requireOffset() }.getOrDefault(0f)
                    val fraction = (offset / width).coerceIn(0f, 1f)
                    alpha = 1f - fraction * SWIPE_ALPHA_FADE
                }
            },
        )
    }
}
