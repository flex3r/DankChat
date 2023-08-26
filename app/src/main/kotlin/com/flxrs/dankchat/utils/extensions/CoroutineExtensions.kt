package com.flxrs.dankchat.utils.extensions

import android.util.Log
import com.flxrs.dankchat.data.UserName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.time.Duration

inline val <T> SharedFlow<T>.firstValue: T
    get() = replayCache.first()

inline val <T> SharedFlow<T>.firstValueOrNull: T?
    get() = replayCache.firstOrNull()

fun MutableSharedFlow<MutableMap<UserName, Int>>.increment(key: UserName, amount: Int) = tryEmit(firstValue.apply {
    val count = get(key) ?: 0
    put(key, count + amount)
})

fun MutableSharedFlow<MutableMap<UserName, Int>>.clear(key: UserName) = tryEmit(firstValue.apply {
    put(key, 0)
})

fun <T> MutableSharedFlow<MutableMap<UserName, T>>.assign(key: UserName, value: T) = tryEmit(firstValue.apply {
    put(key, value)
})

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
