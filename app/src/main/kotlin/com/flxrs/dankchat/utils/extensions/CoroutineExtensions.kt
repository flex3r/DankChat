package com.flxrs.dankchat.utils.extensions

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

inline val <T> SharedFlow<T>.firstValue: T
    get() = replayCache.first()

fun MutableSharedFlow<MutableMap<String, Int>>.increment(key: String, amount: Int) = tryEmit(firstValue.apply {
    val count = get(key) ?: 0
    put(key, count + amount)
})

fun MutableSharedFlow<MutableMap<String, Int>>.clear(key: String) = tryEmit(firstValue.apply {
    put(key, 0)
})

fun <T> MutableSharedFlow<MutableMap<String, T>>.assign(key: String, value: T) = tryEmit(firstValue.apply {
    put(key, value)
})

fun CoroutineScope.timer(interval: Long, action: suspend TimerScope.() -> Unit): Job {
    return launch {
        val scope = TimerScope()

        while (true) {
            try {
                action(scope)
            } catch (ex: Exception) {
                Log.e("TimerScope", Log.getStackTraceString(ex))
            }

            if (scope.isCanceled) {
                break
            }

            delay(interval)
            yield()
        }
    }
}

class TimerScope {
    var isCanceled: Boolean = false
        private set

    fun cancel() {
        isCanceled = true
    }
}