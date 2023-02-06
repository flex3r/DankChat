package com.flxrs.dankchat.data.repo.emote

import com.flxrs.dankchat.data.twitch.emote.GenericEmote

data class Emotes(
    val twitchEmotes: List<GenericEmote> = emptyList(),
    val ffzChannelEmotes: List<GenericEmote> = emptyList(),
    val ffzGlobalEmotes: List<GenericEmote> = emptyList(),
    val bttvChannelEmotes: List<GenericEmote> = emptyList(),
    val bttvGlobalEmotes: List<GenericEmote> = emptyList(),
    val sevenTvChannelEmotes: List<GenericEmote> = emptyList(),
    val sevenTvGlobalEmotes: List<GenericEmote> = emptyList(),
) {

    val emotes: List<GenericEmote> = buildList {
        addAll(twitchEmotes)

        addAll(ffzChannelEmotes)
        addAll(bttvChannelEmotes)
        addAll(sevenTvChannelEmotes)

        addAll(ffzGlobalEmotes)
        addAll(bttvGlobalEmotes)
        addAll(sevenTvGlobalEmotes)
    }.sortedBy { it.code }
}
