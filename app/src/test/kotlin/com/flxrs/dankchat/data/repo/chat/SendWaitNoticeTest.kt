package com.flxrs.dankchat.data.repo.chat

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class SendWaitNoticeTest {
    @Test
    fun `parses remaining slow mode wait`() {
        val message = "This room is in slow mode and you are sending messages too quickly. You will be able to talk again in 10 seconds."

        assertEquals(SendWaitNotice.SlowMode(10), parseSendWaitNotice("msg_slowmode", message))
    }

    @Test
    fun `parses remaining timeout`() {
        assertEquals(SendWaitNotice.Timeout(3600), parseSendWaitNotice("msg_timedout", "You are timed out for 3600 more seconds."))
    }

    @Test
    fun `ignores unrelated notices`() {
        assertNull(parseSendWaitNotice("msg_banned", "You are permanently banned."))
    }
}
