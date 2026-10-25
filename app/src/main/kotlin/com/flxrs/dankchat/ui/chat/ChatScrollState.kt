package com.flxrs.dankchat.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue

@Stable
class ChatScrollState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
    shouldAutoScroll: Boolean = true,
) {
    val listState = LazyListState(firstVisibleItemIndex, firstVisibleItemScrollOffset)
    var shouldAutoScroll by mutableStateOf(shouldAutoScroll)

    fun toSaveable(): List<Any> = listOf(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, shouldAutoScroll)

    companion object {
        fun fromSaveable(saved: List<*>): ChatScrollState = ChatScrollState(
            firstVisibleItemIndex = saved[0] as Int,
            firstVisibleItemScrollOffset = saved[1] as Int,
            shouldAutoScroll = saved[2] as Boolean,
        )
    }
}

/** Held above the layouts, so a chat keeps its scroll position when it is shown in another layout. */
@Stable
class ChatScrollStates(
    private val states: MutableMap<String, ChatScrollState> = mutableMapOf(),
) {
    fun forKey(key: String): ChatScrollState = states.getOrPut(key) { ChatScrollState() }

    fun retain(keys: Set<String>) {
        states.keys.retainAll(keys)
    }

    companion object {
        val Saver: Saver<ChatScrollStates, Any> =
            mapSaver(
                save = { holder -> holder.states.mapValues { (_, state) -> state.toSaveable() } },
                restore = { saved ->
                    val states = saved.mapValuesTo(mutableMapOf()) { (_, state) -> ChatScrollState.fromSaveable(state as List<*>) }
                    ChatScrollStates(states)
                },
            )
    }
}
