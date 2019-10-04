package com.flxrs.dankchat.chat.menu

import com.flxrs.dankchat.service.twitch.emote.GenericEmote

sealed class EmoteItem {
    data class Emote(val emote: GenericEmote) : EmoteItem()
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