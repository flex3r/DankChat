package com.flxrs.dankchat.ui.chat

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class ChatMessageCatchUpTest {
    private fun ids(range: IntRange) = range.map { "m$it" }

    private fun reveal(
        base: List<String>,
        target: List<String>,
        revealCount: Int,
        maxRevealCount: Int = 45,
    ) = revealNewest(base, target, revealCount, maxRevealCount) { it }

    @Test
    fun `few new messages are shown at once`() {
        assertNull(reveal(base = ids(1..10), target = ids(1..13), revealCount = 3))
    }

    @Test
    fun `many new messages are revealed newest first after the base`() {
        assertEquals(ids(1..10) + ids(18..20), reveal(base = ids(1..10), target = ids(1..20), revealCount = 3))
    }

    @Test
    fun `an empty base reveals the newest messages`() {
        assertEquals(ids(18..20), reveal(base = emptyList(), target = ids(1..20), revealCount = 3))
    }

    @Test
    fun `reaching the maximum shows the target`() {
        assertNull(reveal(base = emptyList(), target = ids(1..100), revealCount = 45))
    }

    @Test
    fun `base messages that are revealed again are only shown once`() {
        // Without its newest message in the target, the base overlaps the revealed messages
        assertEquals(ids(1..6) + "m10" + ids(7..9), reveal(base = ids(1..10), target = ids(1..9), revealCount = 3))
    }
}
