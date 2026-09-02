package com.flxrs.dankchat.ui.chat

import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmoteType
import com.flxrs.dankchat.data.twitch.message.TwitchGif
import com.flxrs.dankchat.ui.chat.messages.common.LinkUi
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class TwitchGifContentPartsTest {
    @Test
    fun `disabled Twitch gifs do not create media parts`() {
        val message = "before [GIF] after"
        val gifStart = message.indexOf("[GIF]")
        val gif = TwitchGif("gif", "https://example.com/a.gif", "[GIF]", gifStart..gifStart + 4)

        val parts =
            buildTwitchGifContentParts(
                message = message,
                gifs = listOf(gif),
                links = persistentListOf(),
                emotes = persistentListOf(),
                showTwitchGifs = false,
            )

        assertTrue(parts.isEmpty())
    }

    @Test
    fun `splits mixed text and gifs in protocol order without separator rows`() {
        val message = "before [one] [two] after"
        val gifs =
            listOf(
                TwitchGif("one", "https://example.com/1.gif", "[one]", 7..11),
                TwitchGif("two", "https://example.com/2.gif", "[two]", 13..17),
            )

        val parts = buildTwitchGifContentParts(message, gifs, persistentListOf(), persistentListOf())

        assertEquals(4, parts.size)
        assertEquals("before", assertIs<TwitchGifContentPartUi.Text>(parts[0]).text)
        assertEquals("one", assertIs<TwitchGifContentPartUi.Gif>(parts[1]).gif.id)
        assertEquals("two", assertIs<TwitchGifContentPartUi.Gif>(parts[2]).gif.id)
        assertEquals("after", assertIs<TwitchGifContentPartUi.Text>(parts[3]).text)
    }

    @Test
    fun `gif only message has no empty text parts`() {
        val message = "[GIF]"
        val gif = TwitchGif("gif", "https://example.com/a.gif", message, message.indices)

        val parts = buildTwitchGifContentParts(message, listOf(gif), persistentListOf(), persistentListOf())

        assertEquals(1, parts.size)
        assertIs<TwitchGifContentPartUi.Gif>(parts.single())
    }

    @Test
    fun `rebases contained links and emotes and excludes boundary crossings`() {
        val message = "Kappa https://example.com [GIF] end"
        val gifStart = message.indexOf("[GIF]")
        val gif = TwitchGif("gif", "https://example.com/a.gif", "[GIF]", gifStart..gifStart + 4)
        val emote =
            EmoteUi(
                code = "Kappa",
                urls = persistentListOf("https://example.com/emote.png"),
                position = 0..4,
                isAnimated = false,
                isTwitch = true,
                scale = 1,
                emotes =
                    persistentListOf(
                        ChatMessageEmote(
                            position = 0..4,
                            url = "https://example.com/emote.png",
                            id = "emote",
                            code = "Kappa",
                            scale = 1,
                            type = ChatMessageEmoteType.TwitchEmote,
                        ),
                    ),
            )
        val links =
            persistentListOf(
                LinkUi(6, 25, "https://example.com"),
                LinkUi(gifStart - 1, gifStart + 2, "https://crossing.example.com"),
            )

        val first =
            assertIs<TwitchGifContentPartUi.Text>(
                buildTwitchGifContentParts(message, listOf(gif), links, persistentListOf(emote)).first(),
            )

        assertEquals(0..4, first.emotes.single().position)
        assertEquals(LinkUi(6, 25, "https://example.com"), first.links.single())
        assertTrue(first.links.none { it.url.contains("crossing") })
    }

    @Test
    fun `keeps a trailing emote whose exclusive end equals the text boundary`() {
        val message = "[GIF] Kappa"
        val gif = TwitchGif("gif", "https://example.com/a.gif", "[GIF]", 0..4)
        val emote = emote(position = 6..message.length)

        val text =
            assertIs<TwitchGifContentPartUi.Text>(
                buildTwitchGifContentParts(message, listOf(gif), persistentListOf(), persistentListOf(emote)).last(),
            )

        assertEquals("Kappa", text.text)
        assertEquals(0..5, text.emotes.single().position)
    }

    @Test
    fun `trims only visual spaces at gif boundaries`() {
        listOf(
            Triple("before [GIF] after", listOf("before", "after"), 7..11),
            Triple("[GIF] after", listOf("after"), 0..4),
            Triple("before [GIF]", listOf("before"), 7..11),
        ).forEach { (message, expectedText, range) ->
            val gif = TwitchGif("gif", "https://example.com/a.gif", "[GIF]", range)
            val actual =
                buildTwitchGifContentParts(message, listOf(gif), persistentListOf(), persistentListOf())
                    .filterIsInstance<TwitchGifContentPartUi.Text>()
                    .map { it.text }

            assertEquals(expectedText, actual)
        }
    }

    private fun emote(position: IntRange) = EmoteUi(
        code = "Kappa",
        urls = persistentListOf("https://example.com/emote.png"),
        position = position,
        isAnimated = false,
        isTwitch = true,
        scale = 1,
        emotes =
            persistentListOf(
                ChatMessageEmote(
                    position = position,
                    url = "https://example.com/emote.png",
                    id = "emote",
                    code = "Kappa",
                    scale = 1,
                    type = ChatMessageEmoteType.TwitchEmote,
                ),
            ),
    )
}
