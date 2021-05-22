package com.flxrs.dankchat.chat.menu

import com.flxrs.dankchat.service.twitch.emote.GenericEmote

sealed class EmoteItem {
    data class Emote(val emote: GenericEmote) : EmoteItem(), Comparable<Emote> {
        override fun compareTo(other: Emote): Int {
            return when (val byType = emote.emoteType.compareTo(other.emote.emoteType)) {
                0 -> other.emote.code.compareTo(other.emote.code)
                else -> byType
            }
        }
    }

    data class Header(val title: String) : EmoteItem()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}