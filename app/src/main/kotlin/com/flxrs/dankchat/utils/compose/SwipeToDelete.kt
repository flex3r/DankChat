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
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.R

@Composable
fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val density = LocalDensity.current
    lateinit var state: SwipeToDismissBoxState
    state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> when {
                    state.progress > 0.3f -> onDelete()
                    else                  -> return@rememberSwipeToDismissBoxState false
                }

                SwipeToDismissBoxValue.Settled                                       -> return@rememberSwipeToDismissBoxState false
            }
            true
        },
        positionalThreshold = { with(density) { 84.dp.toPx() } },
    )
    SwipeToDismissBox(
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
