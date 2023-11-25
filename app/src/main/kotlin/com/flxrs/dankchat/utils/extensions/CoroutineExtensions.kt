package com.flxrs.dankchat.utils.extensions

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.time.Duration

suspend fun <T, R> Collection<T>.concurrentMap(block: suspend (T) -> R): List<R> = coroutineScope {
    map { async { block(it) } }.awaitAll()
}

fun CoroutineScope.timer(interval: Duration, action: suspend TimerScope.() -> Unit): Job {
    return launch {
        val scope = TimerScope()

        while (true) {
            try {
                action(scope)
            } catch (ex: Exception) {
                Log.e("TimerScope", Log.getStackTraceString(ex))
            }

            if (scope.isCancelled) {
                break
            }

            delay(interval)
            yield()
        }
    }
}

class TimerScope {
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }
}
