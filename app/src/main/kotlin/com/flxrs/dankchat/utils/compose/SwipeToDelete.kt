package com.flxrs.dankchat.utils.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
    val state = remember {
        SwipeToDismissBoxState(
            positionalThreshold = { with(density) { 84.dp.toPx() } },
            initialValue = SwipeToDismissBoxValue.Settled,
        )
    }
    SwipeToDismissBox(
        gesturesEnabled = enabled,
        enableDismissFromEndToStart = enabled,
        enableDismissFromStartToEnd = enabled,
        modifier = modifier,
        state = state,
        onDismiss = { onDelete() },
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (state.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    SwipeToDismissBoxValue.Settled                                       -> MaterialTheme.colorScheme.surfaceContainer
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, CardDefaults.outlinedShape)
                    .padding(horizontal = 16.dp)
            ) {
                when (state.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(
                        imageVector = Icons.Default.Delete,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(32.dp),
                        contentDescription = stringResource(R.string.remove_command),
                    )

                    SwipeToDismissBoxValue.EndToStart -> Icon(
                        imageVector = Icons.Default.Delete,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(32.dp),
                        contentDescription = stringResource(R.string.remove_command),
                    )

                    SwipeToDismissBoxValue.Settled    -> Unit
                }
            }
        },
        content = content,
    )
}
