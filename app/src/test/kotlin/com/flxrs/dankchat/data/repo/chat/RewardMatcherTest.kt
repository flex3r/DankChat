package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.pubsub.PubSubMessage
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

internal class RewardMatcherTest {
    private val timeSource = TestTimeSource()
    private val matcher = RewardMatcher(timeSource = timeSource, pendingLifetime = 30.seconds)
    private val key = RewardKey("channel".toUserName(), "gigantified-emote-message", "1".toUserId())

    @Test
    fun `redemption arriving first is taken by its message`() {
        val redemption = redemption()
        matcher.onRedemption(key, redemption)

        assertSame(redemption, matcher.take(key))
        assertNull(matcher.take(key))
    }

    @Test
    fun `message arriving first waits for its redemption`() = runTest {
        val redemption = redemption()
        val result = async { matcher.await(key, 3.seconds) }
        yield()

        matcher.onRedemption(key, redemption)

        assertSame(redemption, result.await())
        assertNull(matcher.take(key))
    }

    @Test
    fun `redemptions of another channel are not matched`() = runTest {
        matcher.onRedemption(key.copy(channel = "other".toUserName()), redemption())

        assertNull(matcher.await(key, 3.seconds))
    }

    @Test
    fun `redemption after a timed out wait is kept for the next message`() = runTest {
        assertNull(matcher.await(key, 3.seconds))

        val redemption = redemption()
        matcher.onRedemption(key, redemption)

        assertSame(redemption, matcher.take(key))
    }

    @Test
    fun `concurrent redemptions are matched in order`() {
        val first = redemption()
        val second = redemption()
        matcher.onRedemption(key, first)
        matcher.onRedemption(key, second)

        assertSame(first, matcher.take(key))
        assertSame(second, matcher.take(key))
    }

    @Test
    fun `unmatched redemptions expire`() {
        matcher.onRedemption(key, redemption())
        timeSource += 31.seconds

        assertNull(matcher.take(key))
    }

    private fun redemption(): PubSubMessage.PointRedemption = mockk()
}
