package com.flxrs.dankchat.ui.main.sheet

import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import com.flxrs.dankchat.ui.chat.ChatScrollState
import com.flxrs.dankchat.ui.chat.history.HistoryChannel

/** Held above the layouts, so an open sheet keeps its tab and scroll positions when it is shown in another layout. */
@Stable
class FullScreenSheetUiStates(
    private var openId: Long = 0L,
    private var mentionTab: Int? = null,
    private val scrollStates: MutableMap<String, ChatScrollState> = mutableMapOf(),
) {
    fun mentionTab(
        openId: Long,
        initialTab: Int,
    ): Int {
        resetForOpening(openId)
        return mentionTab ?: initialTab
    }

    fun setMentionTab(
        openId: Long,
        tab: Int,
    ) {
        resetForOpening(openId)
        mentionTab = tab
    }

    fun mentionScrollState(
        openId: Long,
        isWhisperTab: Boolean,
    ): ChatScrollState = scrollState(openId, if (isWhisperTab) "whispers" else "mentions")

    fun repliesScrollState(openId: Long): ChatScrollState = scrollState(openId, "replies")

    fun historyScrollState(
        openId: Long,
        channel: HistoryChannel,
    ): ChatScrollState {
        val key =
            when (channel) {
                HistoryChannel.Global -> "history"
                is HistoryChannel.Channel -> "history:${channel.name.value}"
            }
        return scrollState(openId, key)
    }

    private fun scrollState(
        openId: Long,
        key: String,
    ): ChatScrollState {
        resetForOpening(openId)
        return scrollStates.getOrPut(key) { ChatScrollState() }
    }

    private fun resetForOpening(openId: Long) {
        if (openId != this.openId) {
            this.openId = openId
            mentionTab = null
            scrollStates.clear()
        }
    }

    companion object {
        private const val OPEN_ID_KEY = "openId"
        private const val MENTION_TAB_KEY = "mentionTab"
        private const val SCROLL_STATES_KEY = "scrollStates"

        val Saver: Saver<FullScreenSheetUiStates, Any> =
            mapSaver(
                save = { holder ->
                    mapOf(
                        OPEN_ID_KEY to holder.openId,
                        MENTION_TAB_KEY to holder.mentionTab,
                        SCROLL_STATES_KEY to HashMap(holder.scrollStates.mapValues { (_, state) -> state.toSaveable() }),
                    )
                },
                restore = { saved ->
                    val scrollStates =
                        (saved[SCROLL_STATES_KEY] as Map<*, *>)
                            .entries
                            .associateTo(mutableMapOf()) { (key, state) -> key as String to ChatScrollState.fromSaveable(state as List<*>) }
                    FullScreenSheetUiStates(
                        openId = saved[OPEN_ID_KEY] as Long,
                        mentionTab = saved[MENTION_TAB_KEY] as Int?,
                        scrollStates = scrollStates,
                    )
                },
            )
    }
}
