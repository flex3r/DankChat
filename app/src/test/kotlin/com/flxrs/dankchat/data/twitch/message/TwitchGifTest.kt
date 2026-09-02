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
    fun `parses documented gif and preserves url query`() {
        val message = "[Y A Y Yes GIF by Djemilah Birnie]"
        val url = "https://example.com/gif.gif?width=480&token=a%2Bb"

        val gif = parseTwitchGifTag(message, "0-33|joSNxeswxuc74Juo8X|$url").single()

        assertEquals("joSNxeswxuc74Juo8X", gif.id)
        assertEquals(0..33, gif.position)
        assertEquals(message, gif.altText)
        assertEquals(url, gif.url)
    }

    @Test
    fun `converts code point positions to utf16`() {
        val message = "😀[GIF] after"

        val gif = parseTwitchGifTag(message, "1-5|id|https://example.com/a.gif").single()

        assertEquals(2..6, gif.position)
        assertEquals("[GIF]", gif.altText)
    }

    @Test
    fun `rejects malformed insecure and overlapping entries independently`() {
        val message = "one two three"
        val gifs =
            parseTwitchGifTag(
                message,
                "0-2|one|https://example.com/1.gif,2-6|overlap|https://example.com/2.gif,4-6|two|https://example.com/3.gif,8-12||https://example.com/4.gif,8-12|bad|http://example.com/4.gif,bad",
            )

        assertEquals(listOf("one", "two"), gifs.map { it.id })
        assertEquals(listOf("one", "two"), gifs.map { it.altText })
    }

    @Test
    fun `edits shift preserve or drop gif ranges according to policy`() {
        val gif = TwitchGif("id", "https://example.com/a.gif", "[GIF]", 5..9)

        assertEquals(7..11, listOf(gif).applyTextEdits(listOf(PositionedTextEdit(0, 1, 3))).single().position)
        assertEquals(5..9, listOf(gif).applyTextEdits(listOf(PositionedTextEdit(10, 10, 2))).single().position)
        assertTrue(listOf(gif).applyTextEdits(listOf(PositionedTextEdit(6, 8, 1))).isEmpty())
        assertEquals(
            5..8,
            listOf(gif)
                .applyTextEdits(
                    listOf(PositionedTextEdit(6, 8, 1)),
                    PositionedTextEditOverlapPolicy.PreserveContainedEdits,
                ).single()
                .position,
        )
        assertEquals(
            3..7,
            listOf(gif)
                .applyTextEdits(
                    listOf(
                        PositionedTextEdit(0, 1, 0),
                        PositionedTextEdit(2, 3, 0),
                    ),
                ).single()
                .position,
        )
    }
}
