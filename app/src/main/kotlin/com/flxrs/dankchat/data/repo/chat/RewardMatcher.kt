package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.pubsub.PubSubMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal data class RewardKey(
    val channel: UserName,
    val rewardId: String,
    val userId: UserId?,
)

/**
 * Pairs PubSub redemptions with the IRC message they belong to. Either side can arrive first, an IRC message waits for its
 * redemption while a redemption is kept until its IRC message arrives or it expires.
 */
internal class RewardMatcher(
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val pendingLifetime: Duration = PENDING_LIFETIME,
) {
    private val lock = Any()
    private val pending = mutableMapOf<RewardKey, ArrayDeque<PendingRedemption>>()
    private val waiters = mutableMapOf<RewardKey, ArrayDeque<CompletableDeferred<PubSubMessage.PointRedemption>>>()

    fun onRedemption(
        key: RewardKey,
        redemption: PubSubMessage.PointRedemption,
    ) {
        synchronized(lock) {
            pruneExpired()
            val waiter = waiters.removeFirst(key)
            when (waiter) {
                null -> pending.getOrPut(key) { ArrayDeque() }.addLast(PendingRedemption(redemption, timeSource.markNow()))
                else -> waiter.complete(redemption)
            }
        }
    }

    fun take(key: RewardKey): PubSubMessage.PointRedemption? = synchronized(lock) {
        pruneExpired()
        pending.removeFirst(key)?.redemption
    }

    suspend fun await(
        key: RewardKey,
        timeout: Duration,
    ): PubSubMessage.PointRedemption? {
        val waiter =
            synchronized(lock) {
                pruneExpired()
                val redemption = pending.removeFirst(key)?.redemption
                if (redemption != null) {
                    return redemption
                }
                CompletableDeferred<PubSubMessage.PointRedemption>().also { waiters.getOrPut(key) { ArrayDeque() }.addLast(it) }
            }

        val redemption = withTimeoutOrNull(timeout) { waiter.await() }
        if (redemption != null) {
            return redemption
        }

        val stillWaiting =
            synchronized(lock) {
                val queue = waiters[key]
                val removed = queue?.remove(waiter) == true
                if (queue != null && queue.isEmpty()) {
                    waiters.remove(key)
                }
                removed
            }

        // The redemption can arrive between the timeout and the removal
        return when {
            stillWaiting -> null
            else -> waiter.await()
        }
    }

    private fun pruneExpired() {
        val iterator = pending.values.iterator()
        while (iterator.hasNext()) {
            val queue = iterator.next()
            queue.removeAll { it.receivedAt.elapsedNow() > pendingLifetime }
            if (queue.isEmpty()) {
                iterator.remove()
            }
        }
    }

    private fun <T> MutableMap<RewardKey, ArrayDeque<T>>.removeFirst(key: RewardKey): T? {
        val queue = this[key] ?: return null
        val first = queue.removeFirstOrNull()
        if (queue.isEmpty()) {
            remove(key)
        }
        return first
    }

    private data class PendingRedemption(
        val redemption: PubSubMessage.PointRedemption,
        val receivedAt: TimeMark,
    )

    private companion object {
        val PENDING_LIFETIME = 30.seconds
    }
}
