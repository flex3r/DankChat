package com.flxrs.dankchat.ui.chat

import com.flxrs.dankchat.data.twitch.message.TwitchGif
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TwitchGifContentPartsTest {
    @Test
    fun `disabled Twitch gifs or messages without gifs do not create parts`() {
        val gif = TwitchGif("gif", "https://example.com/a.gif", "[GIF]", 7..11)

        assertTrue(buildTwitchGifContentParts("before [GIF] after", listOf(gif), showTwitchGifs = false).isEmpty())
        assertTrue(buildTwitchGifContentParts("before after", emptyList(), showTwitchGifs = true).isEmpty())
    }

    @Test
    fun `splits text around gifs and trims the separating spaces`() {
        val message = "before [one] [two] after"
        val one = TwitchGif("one", "https://example.com/1.gif", "[one]", 7..11)
        val two = TwitchGif("two", "https://example.com/2.gif", "[two]", 13..17)

        val parts = buildTwitchGifContentParts(message, listOf(one, two), showTwitchGifs = true)

        assertEquals(
            listOf(
                TwitchGifContentPartUi.Text(0, 6),
                TwitchGifContentPartUi.Gif(TwitchGifUi("one", one.url, "[one]")),
                TwitchGifContentPartUi.Gif(TwitchGifUi("two", two.url, "[two]")),
                TwitchGifContentPartUi.Text(19, 24),
            ),
            parts,
        )
        assertEquals("after", message.substring(19, 24))
    }

    @Test
    fun `a message starting or ending with a gif has no empty text parts`() {
        val gif = TwitchGif("gif", "https://example.com/a.gif", "[GIF]", 0..4)

        val parts = buildTwitchGifContentParts("[GIF]", listOf(gif), showTwitchGifs = true)

        assertEquals(listOf<TwitchGifContentPartUi>(TwitchGifContentPartUi.Gif(TwitchGifUi("gif", gif.url, "[GIF]"))), parts)
    }
}
