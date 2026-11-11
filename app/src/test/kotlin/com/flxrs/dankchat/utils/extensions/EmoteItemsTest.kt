package com.flxrs.dankchat.utils.extensions

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.emote.EmoteType
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.ui.chat.emotemenu.EmoteItem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class EmoteItemsTest {
    private val channel = UserName("channel")

    @Test
    fun `emotes of a section are ordered by code ignoring case`() {
        val emotes = listOf("OMEGALUL", "Okayge", "catJAM", "monkaS").map { emote(it, EmoteType.GlobalBTTVEmote) }

        val items = emotes.toEmoteItems()

        assertEquals(expected = listOf("catJAM", "monkaS", "Okayge", "OMEGALUL"), actual = items.codes())
    }

    @Test
    fun `bit and follower emotes come after the subscriber emotes of a channel`() {
        val emotes = listOf(
            emote("aBits", EmoteType.ChannelTwitchBitEmote(channel)),
            emote("zSub", EmoteType.ChannelTwitchEmote(channel)),
            emote("bFollower", EmoteType.ChannelTwitchFollowerEmote(channel)),
            emote("cSub", EmoteType.ChannelTwitchEmote(channel)),
        )

        val items = emotes.toEmoteItemsWithFront(channel)

        assertEquals(expected = EmoteItem.Header(channel.value), actual = items.first())
        assertEquals(expected = listOf("cSub", "zSub", "aBits", "bFollower"), actual = items.codes())
    }

    @Test
    fun `recent emotes stay in usage order`() {
        val emotes = listOf("monkaS", "catJAM", "Okayge").map { emote(it, EmoteType.RecentUsageEmote) }

        val items = emotes.toRecentEmoteItems()

        assertEquals(expected = listOf("monkaS", "catJAM", "Okayge"), actual = items.codes())
    }

    @Test
    fun `no recent emotes produce no section`() {
        assertEquals(expected = emptyList(), actual = emptyList<GenericEmote>().toRecentEmoteItems())
    }

    private fun emote(
        code: String,
        type: EmoteType,
    ): GenericEmote = GenericEmote(code = code, url = "", lowResUrl = "", id = code, scale = 1, emoteType = type)

    private fun List<EmoteItem>.codes(): List<String> = filterIsInstance<EmoteItem.Emote>().map { it.emote.code }
}
