package com.flxrs.dankchat.data.notification

import com.flxrs.dankchat.data.toUserName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class SpokenUsernameMentionsTest {
    @Test
    fun `mention is read without prefix and numbers`() {
        assertEquals(expected = "Hello user", actual = "Hello @user123".withSpokenUsernameMentions { false })
    }

    @Test
    fun `punctuation after a mention is kept`() {
        assertEquals(expected = "user, look at this", actual = "@user123, look at this".withSpokenUsernameMentions { false })
    }

    @Test
    fun `known chatter without prefix is read without numbers`() {
        val chatters = setOf("user123".toUserName())

        assertEquals(expected = "Hello user", actual = "Hello user123".withSpokenUsernameMentions { it in chatters })
    }

    @Test
    fun `numbers in other words are kept`() {
        assertEquals(expected = "see you at 9 in room 42b", actual = "see you at 9 in room 42b".withSpokenUsernameMentions { false })
    }

    @Test
    fun `mention of a name made of numbers keeps the numbers`() {
        assertEquals(expected = "Hello 12345", actual = "Hello @12345".withSpokenUsernameMentions { false })
    }
}
