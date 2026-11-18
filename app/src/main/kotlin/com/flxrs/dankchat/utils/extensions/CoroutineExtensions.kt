package com.flxrs.dankchat.utils.extensions

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.time.Duration

private val logger = KotlinLogging.logger("CoroutineExtensions")

suspend fun <T, R> Collection<T>.concurrentMap(block: suspend (T) -> R): List<R> = coroutineScope {
    map { async { block(it) } }.awaitAll()
}

/** [runCatching] that rethrows [CancellationException] instead of swallowing coroutine cancellation. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = runCatching(block).onFailure {
    if (it is CancellationException) {
        throw it
    }
}

/** Runs [action] once per request, requests sent while it runs collapse into one more run. Closing the channel completes the job. */
fun CoroutineScope.launchConflated(action: suspend () -> Unit): SendChannel<Unit> {
    val requests = Channel<Unit>(Channel.CONFLATED)
    launch {
        requests.consumeEach { action() }
    }
    return requests
}

fun CoroutineScope.timer(
    interval: Duration,
    action: suspend TimerScope.() -> Unit,
): Job = launch {
    val scope = TimerScope()

    while (true) {
        try {
            action(scope)
        } catch (ex: Exception) {
            logger.error(ex) { "TimerScope error" }
        }

        if (scope.isCancelled) {
            break
        }

        delay(interval)
        yield()
    }
}

class TimerScope {
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }
}
