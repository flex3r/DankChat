package com.flxrs.dankchat.chat.menu

import com.flxrs.dankchat.data.twitch.emote.GenericEmote

sealed class EmoteItem {

    open val textToInsert: String? = null

    data class Emote(val emote: GenericEmote) : EmoteItem(), Comparable<Emote> {

        override val textToInsert = emote.code
        override fun compareTo(other: Emote): Int {
            return when (val byType = emote.emoteType.compareTo(other.emote.emoteType)) {
                0    -> other.emote.code.compareTo(other.emote.code)
                else -> byType
            }
        }
    }

    data class Header(val title: String) : EmoteItem()

    data class Emoji(val emoji: String) : EmoteItem() {
        override val textToInsert = emoji
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int = javaClass.hashCode()
    operator fun plus(list: List<EmoteItem>): List<EmoteItem> = listOf(this) + list
}
