package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.UserName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single
import kotlin.math.ceil

@Single
class SendWaitRepository {
    private val waits = MutableStateFlow<Map<UserName, SendWait>>(emptyMap())

    fun getRemainingSeconds(channel: UserName): Flow<Int?> = waits
        .map { it[channel] }
        .distinctUntilChanged()
        .flatMapLatest { wait ->
            if (wait == null) {
                flowOf(null)
            } else {
                flow {
                    while (true) {
                        val remainingMillis = wait.endsAtElapsedMillis - elapsedRealtimeMillis()
                        if (remainingMillis <= 0) {
                            clear(channel, wait)
                            emit(null)
                            break
                        }
                        emit(ceil(remainingMillis / 1000.0).toInt())
                        delay(1000)
                    }
                }
            }
        }

    fun startSlowMode(
        channel: UserName,
        durationSeconds: Int?,
        hasHighRateLimit: Boolean,
    ) {
        when {
            hasHighRateLimit || durationSeconds == null -> clear(channel, SendWaitReason.SlowMode)
            else -> set(channel, durationSeconds, SendWaitReason.SlowMode)
        }
    }

    fun startTimeout(
        channel: UserName,
        durationSeconds: Int,
    ) = set(channel, durationSeconds, SendWaitReason.Timeout)

    fun onRoomStateChanged(
        channel: UserName,
        slowModeSeconds: Int?,
    ) {
        if (slowModeSeconds == null) {
            clear(channel, SendWaitReason.SlowMode)
        }
    }

    fun onHighRateLimitChanged(
        channel: UserName,
        hasHighRateLimit: Boolean,
    ) {
        if (hasHighRateLimit) {
            clear(channel, SendWaitReason.SlowMode)
        }
    }

    private fun set(
        channel: UserName,
        durationSeconds: Int,
        reason: SendWaitReason,
    ) {
        if (durationSeconds <= 0) return
        val wait = SendWait(elapsedRealtimeMillis() + durationSeconds * 1000L, reason)
        waits.update { it + (channel to wait) }
    }

    private fun clear(
        channel: UserName,
        reason: SendWaitReason,
    ) {
        waits.update { current ->
            if (current[channel]?.reason == reason) current - channel else current
        }
    }

    private fun clear(
        channel: UserName,
        wait: SendWait,
    ) {
        waits.update { current ->
            if (current[channel] == wait) current - channel else current
        }
    }
}

private data class SendWait(
    val endsAtElapsedMillis: Long,
    val reason: SendWaitReason,
)

private enum class SendWaitReason { SlowMode, Timeout }

private fun elapsedRealtimeMillis(): Long = System.nanoTime() / 1_000_000
