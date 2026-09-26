package com.flxrs.dankchat.ui.chat.messages.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class UsernameMentionStylingTest {
    @Test
    fun `preserves links while styling usernames`() {
        val text = "@forsen https://example.com"
        val mentionColor = Color.Red
        val linkColor = Color.Blue

        val result =
            buildAnnotatedString {
                appendWithLinks(
                    text = text,
                    segmentStart = 0,
                    links = persistentListOf(LinkUi(8, text.length, "https://example.com")),
                    linkColor = linkColor,
                    usernameMentions = listOf(ResolvedUsernameMention(0, 7, mentionColor, true, "|forsen|forsen|iore")),
                )
            }

        assertEquals(text, result.text)
        assertEquals(
            mentionColor,
            result.spanStyles
                .single { it.start == 0 }
                .item.color,
        )
        assertEquals(
            FontWeight.Bold,
            result.spanStyles
                .single { it.start == 0 }
                .item.fontWeight,
        )
        assertEquals(
            linkColor,
            result.spanStyles
                .single { it.start == 8 }
                .item.color,
        )
        assertEquals(
            "|forsen|forsen|iore",
            result.getStringAnnotations(MENTIONED_USER_ANNOTATION_TAG, 0, 0).single().item,
        )
        assertEquals(
            "https://example.com",
            result.getStringAnnotations(URL_ANNOTATION_TAG, 8, 8).single().item,
        )
    }
}
