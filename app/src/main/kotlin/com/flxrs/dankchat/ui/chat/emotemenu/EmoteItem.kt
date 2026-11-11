package com.flxrs.dankchat.ui.chat.emotemenu

import androidx.compose.runtime.Immutable
import com.flxrs.dankchat.data.twitch.emote.GenericEmote

@Immutable
sealed class EmoteItem {
    data class Emote(
        val emote: GenericEmote,
    ) : EmoteItem(),
        Comparable<Emote> {
        override fun compareTo(other: Emote): Int = when (val byType = emote.emoteType.compareTo(other.emote.emoteType)) {
            0 -> emote.code.compareTo(other.emote.code, ignoreCase = true)
            else -> byType
        }
    }

    data class Header(
        val title: String,
    ) : EmoteItem()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return javaClass == other?.javaClass
    }

    override fun hashCode(): Int = javaClass.hashCode()

    operator fun plus(list: List<EmoteItem>): List<EmoteItem> = listOf(this) + list
}
