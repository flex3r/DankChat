package com.flxrs.dankchat.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// Composing a page of new rows in one frame stalls it, so new rows are added a few per frame
private const val CATCH_UP_ROWS_PER_FRAME = 3

// Enough rows to cover the viewport, the remaining new rows are offscreen and added at once
private const val CATCH_UP_MAX_ROWS = 45

/**
 * Shows [target], adding new messages a few rows per frame, newest first so the bottom of the chat is complete right away.
 * With [startEmpty] the first messages are added the same way, for pages composed offscreen.
 */
@Composable
internal fun rememberCaughtUpMessages(
    target: ImmutableList<ChatMessageUiState>,
    isEnabled: Boolean,
    startEmpty: Boolean,
): ImmutableList<ChatMessageUiState> {
    val catchUp = remember { ChatMessageCatchUp(initial = if (startEmpty) persistentListOf() else target) }
    val messages =
        when {
            isEnabled -> catchUp.messagesFor(target)
            else -> target
        }
    SideEffect {
        if (messages === target) {
            catchUp.onCaughtUp(target)
        }
    }
    val isCatchingUp = messages !== target
    LaunchedEffect(isCatchingUp) {
        catchUp.reveal(isCatchingUp)
    }
    return messages
}

@Stable
internal class ChatMessageCatchUp(
    initial: ImmutableList<ChatMessageUiState>,
) {
    // Last list shown completely, only updated once a composition showing it was applied
    private var base = initial
    private var revealed by mutableIntStateOf(0)

    fun messagesFor(target: ImmutableList<ChatMessageUiState>): ImmutableList<ChatMessageUiState> {
        val partial = revealNewest(base, target, revealed + CATCH_UP_ROWS_PER_FRAME, CATCH_UP_MAX_ROWS, ChatMessageUiState::id)
        return partial?.toImmutableList() ?: target
    }

    fun onCaughtUp(target: ImmutableList<ChatMessageUiState>) {
        base = target
    }

    suspend fun reveal(isCatchingUp: Boolean) {
        if (!isCatchingUp) {
            revealed = 0
            return
        }
        while (true) {
            withFrameNanos { }
            revealed += CATCH_UP_ROWS_PER_FRAME
        }
    }
}

/**
 * Returns [base] followed by the newest [revealCount] items of [target], or null when [target] can be shown as is
 * because it has at most [revealCount] items newer than [base] or [revealCount] reached [maxRevealCount].
 */
internal fun <T> revealNewest(
    base: List<T>,
    target: List<T>,
    revealCount: Int,
    maxRevealCount: Int,
    key: (T) -> String,
): List<T>? {
    if (base === target || revealCount >= maxRevealCount) {
        return null
    }
    val newestBaseKey = base.lastOrNull()?.let(key)
    // Without the newest base item in target every item counts as new
    val pending = target.size - 1 - target.indexOfLast { key(it) == newestBaseKey }
    if (pending <= revealCount) {
        return null
    }
    val revealed = target.subList(target.size - revealCount, target.size)
    // Lazy list keys must stay unique, even if base and the revealed items overlap
    val revealedKeys = revealed.map(key).toSet()
    return base.filter { key(it) !in revealedKeys } + revealed
}
