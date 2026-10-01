package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.toUserName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class SendWaitRepositoryTest {
    private val channel = "forsen".toUserName()
    private val repository = SendWaitRepository()

    @Test
    fun `slow mode starts a countdown after sending`() = runTest {
        repository.onOwnMessage(channel, slowModeSeconds = 30, hasHighRateLimit = false)

        assertEquals(30, repository.getRemainingSeconds(channel).first())
    }

    @Test
    fun `slow mode does not count down for moderators or VIPs`() = runTest {
        repository.onOwnMessage(channel, slowModeSeconds = 30, hasHighRateLimit = true)

        assertNull(repository.getRemainingSeconds(channel).first())
    }

    @Test
    fun `gaining a high rate limit clears a running slow mode countdown`() = runTest {
        repository.onOwnMessage(channel, slowModeSeconds = 30, hasHighRateLimit = false)

        repository.onHighRateLimitChanged(channel, hasHighRateLimit = true)

        assertNull(repository.getRemainingSeconds(channel).first())
    }

    @Test
    fun `own message ends a running timeout countdown outside of slow mode`() = runTest {
        repository.startTimeout(channel, durationSeconds = 600)

        repository.onOwnMessage(channel, slowModeSeconds = null, hasHighRateLimit = false)

        assertNull(repository.getRemainingSeconds(channel).first())
    }

    @Test
    fun `own message replaces a running timeout countdown with slow mode`() = runTest {
        repository.startTimeout(channel, durationSeconds = 600)

        repository.onOwnMessage(channel, slowModeSeconds = 30, hasHighRateLimit = false)

        assertEquals(30, repository.getRemainingSeconds(channel).first())
    }

    @Test
    fun `gaining a high rate limit clears a running timeout countdown`() = runTest {
        repository.startTimeout(channel, durationSeconds = 600)

        repository.onHighRateLimitChanged(channel, hasHighRateLimit = true)

        assertNull(repository.getRemainingSeconds(channel).first())
    }

    @Test
    fun `disabling slow mode clears only a slow mode countdown`() = runTest {
        repository.onOwnMessage(channel, slowModeSeconds = 30, hasHighRateLimit = false)
        repository.onRoomStateChanged(channel, slowModeSeconds = null)

        assertNull(repository.getRemainingSeconds(channel).first())

        repository.startTimeout(channel, durationSeconds = 30)
        repository.onRoomStateChanged(channel, slowModeSeconds = null)

        assertEquals(30, repository.getRemainingSeconds(channel).first())
    }
}
