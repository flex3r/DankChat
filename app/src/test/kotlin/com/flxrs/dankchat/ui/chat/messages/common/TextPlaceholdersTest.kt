package com.flxrs.dankchat.ui.chat.messages.common

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentMapOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class TextPlaceholdersTest {
    private val density = Density(density = 2f)

    @Test
    fun `inline content with known dimensions becomes placeholders in text order`() {
        val text =
            buildAnnotatedString {
                appendInlineContent("BADGE_0", " ")
                append("user: ")
                appendInlineContent("EMOTE_1..5", "forsenE")
            }
        val dimensions =
            persistentMapOf(
                "EMOTE_1..5" to EmoteDimensions("EMOTE_1..5", widthPx = 56, heightPx = 28),
                "BADGE_0" to EmoteDimensions("BADGE_0", widthPx = 28, heightPx = 28),
            )

        val placeholders = buildPlaceholders(dimensions, text, density)

        assertEquals(listOf("BADGE_0", "EMOTE_1..5"), placeholders.ids)
        assertEquals(listOf(0 to 1, 7 to 14), placeholders.ranges.map { it.start to it.end })
        assertEquals(
            28f,
            placeholders.ranges[1]
                .item.width.value,
        )
    }

    @Test
    fun `inline content without dimensions keeps its text`() {
        val text = buildAnnotatedString { appendInlineContent("EMOTE_0..6", "forsenE") }

        val placeholders = buildPlaceholders(persistentMapOf(), text, density)

        assertEquals(emptyList(), placeholders.ids)
    }

    @Test
    fun `spacers are sized by their id`() {
        val text = buildAnnotatedString { appendInlineSpacer(6.dp) }

        val placeholders = buildPlaceholders(persistentMapOf(), text, density)

        assertEquals(1, placeholders.ids.size)
        assertEquals(
            6f,
            placeholders.ranges
                .single()
                .item.width.value,
        )
    }
}
