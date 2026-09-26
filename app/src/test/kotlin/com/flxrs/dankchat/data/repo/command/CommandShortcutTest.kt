package com.flxrs.dankchat.data.repo.command

import com.flxrs.dankchat.data.toUserName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class CommandShortcutTest {
    @Test
    fun `reply command expands to whisper command`() {
        assertEquals("/w qbit ", expandReplyToLastWhisper("/r ", "qbit".toUserName()))
    }

    @Test
    fun `reply command is case insensitive and preserves message`() {
        assertEquals("/w qbit hello", expandReplyToLastWhisper("/R hello", "qbit".toUserName()))
    }

    @Test
    fun `reply command without a received whisper does not expand`() {
        assertNull(expandReplyToLastWhisper("/r ", null))
    }

    @Test
    fun `reply command does not expand before a space is entered`() {
        assertNull(expandReplyToLastWhisper("/r", "qbit".toUserName()))
    }

    @Test
    fun `reply command does not expand within a message`() {
        assertNull(expandReplyToLastWhisper("hello /r ", "qbit".toUserName()))
    }
}
