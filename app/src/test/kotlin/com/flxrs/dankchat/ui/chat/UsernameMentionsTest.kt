package com.flxrs.dankchat.ui.chat

import com.flxrs.dankchat.data.toUserName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class UsernameMentionsTest {
    @Test
    fun `finds usernames at word boundaries`() {
        val mentions = findUsernameMentions("hello @forsen and @Iore")

        assertEquals(
            listOf(
                UsernameMention(6, 13, "forsen".toUserName()),
                UsernameMention(18, 23, "Iore".toUserName()),
            ),
            mentions,
        )
    }

    @Test
    fun `excludes trailing punctuation from username`() {
        assertEquals(
            listOf(UsernameMention(0, 7, "forsen".toUserName())),
            findUsernameMentions("@forsen!!!"),
        )
    }

    @Test
    fun `ignores at signs inside other text`() {
        assertEquals(emptyList(), findUsernameMentions("test@example.com and hello@forsen"))
    }

    @Test
    fun `ignores invalid username tokens`() {
        assertEquals(emptyList(), findUsernameMentions("@forsen-name @Iore/other"))
    }

    @Test
    fun `finds known chatters without the at sign`() {
        val chatters = setOf("forsen".toUserName(), "iore".toUserName())

        assertEquals(
            listOf(
                UsernameMention(0, 6, "Forsen".toUserName()),
                UsernameMention(16, 20, "iore".toUserName()),
            ),
            findUsernameMentions("Forsen, what is iore: doing") { it in chatters },
        )
    }

    @Test
    fun `ignores unknown words and partial matches without the at sign`() {
        val chatters = setOf("forsen".toUserName())

        assertEquals(emptyList(), findUsernameMentions("forsenE forsen-name xforsen hello") { it in chatters })
    }
}
