package com.flxrs.dankchat.data.twitch.message

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TwitchGifTest {
    @Test
    fun `uses giphy mobile rendition while preserving query parameters`() {
        val original =
            "https://media4.giphy.com/media/joSNxeswxuc74Juo8X/giphy.gif?cid=test&rid=giphy.gif&ct=g"

        assertEquals(
            "https://media4.giphy.com/media/joSNxeswxuc74Juo8X/200.webp?cid=test&rid=200.webp&ct=g",
            original.toTwitchGifLoadUrl(),
        )
    }

    @Test
    fun `does not rewrite unknown hosts or non-original giphy paths`() {
        val unknown = "https://example.com/media/id/giphy.gif?rid=giphy.gif"
        val existingRendition = "https://media4.giphy.com/media/id/100.webp?rid=100.webp"

        assertEquals(unknown, unknown.toTwitchGifLoadUrl())
        assertEquals(existingRendition, existingRendition.toTwitchGifLoadUrl())
    }

    @Test
    fun `parses documented gif entries and preserves the url query`() {
        val url = "https://example.com/gif.gif?width=480&token=a%2Bb"

        assertEquals(listOf(TwitchGifWithPosition("joSNxeswxuc74Juo8X", url, 0..33)), parseTwitchGifTag("0-33|joSNxeswxuc74Juo8X|$url"))
    }

    @Test
    fun `skips invalid gif entries`() {
        val gifs =
            parseTwitchGifTag(
                "2-1|inverted|https://example.com/1.gif,0-2||https://example.com/2.gif,0-2|insecure|http://example.com/3.gif,bad,4-6|valid|https://example.com/4.gif",
            )

        assertEquals(listOf("valid"), gifs.map { it.id })
        assertTrue(parseTwitchGifTag("").isEmpty())
    }
}
