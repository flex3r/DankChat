package com.flxrs.dankchat.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.ui.chat.messages.PrivMessageComposable
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PinnedMessageBanner(
    state: PinnedMessageUiState.Expanded,
    remainingTime: StateFlow<String?>,
    fontSize: Float,
    animateGifs: Boolean,
    callbacks: ChatScreenCallbacks,
    onCollapse: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.pinned_by, state.pinnedBy.value),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .weight(1f),
                )
                PinnedMessageCountdown(remainingTime)
                if (state.canModerate) {
                    TextButton(onClick = onUnpin) {
                        Text(text = stringResource(R.string.pinned_message_unpin))
                    }
                }
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.ExpandLess,
                        contentDescription = stringResource(R.string.pinned_message_collapse),
                    )
                }
            }
            PrivMessageComposable(
                message = state.message,
                fontSize = fontSize,
                onUserClick = callbacks.onUserClick,
                onMessageLongClick = callbacks.onMessageLongClick,
                onEmoteClick = callbacks.onEmoteClick,
                onReplyClick = callbacks.onReplyClick,
                animateGifs = animateGifs,
                maxLines = PINNED_MESSAGE_MAX_LINES,
            )
        }
    }
}

// Collects the per-second countdown on its own so only this text recomposes while it ticks
@Composable
private fun PinnedMessageCountdown(remainingTime: StateFlow<String?>) {
    val text = remainingTime.collectAsStateWithLifecycle().value
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

private const val PINNED_MESSAGE_MAX_LINES = 3
