package com.flxrs.dankchat.utils.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.R

@Composable
fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val density = LocalDensity.current
    lateinit var state: SwipeToDismissBoxState
    state = remember {
        SwipeToDismissBoxState(
            confirmValueChange = {
                when (it) {
                    SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> when {
                        state.currentValue == state.targetValue -> return@SwipeToDismissBoxState false
                        state.progress > 0.3f   -> onDelete()

                        else                    -> return@SwipeToDismissBoxState false
                    }

                    SwipeToDismissBoxValue.Settled                                       -> return@SwipeToDismissBoxState false
                }
                true
            },
            positionalThreshold = { with(density) { 84.dp.toPx() } },
            initialValue = SwipeToDismissBoxValue.Settled,
            density = density,
        )
    }
    SwipeToDismissBox(
        gesturesEnabled = enabled,
        enableDismissFromEndToStart = enabled,
        enableDismissFromStartToEnd = enabled,
        modifier = modifier,
        state = state,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (state.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    SwipeToDismissBoxValue.Settled                                       -> MaterialTheme.colorScheme.surfaceContainer
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, CardDefaults.outlinedShape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(Icons.Default.Delete, modifier = Modifier.size(32.dp), contentDescription = stringResource(R.string.remove_command))
                Icon(Icons.Default.Delete, modifier = Modifier.size(32.dp), contentDescription = stringResource(R.string.remove_command))
            }
        },
        content = content,
    )
}
