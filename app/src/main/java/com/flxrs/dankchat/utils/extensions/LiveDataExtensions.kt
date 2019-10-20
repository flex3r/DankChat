package com.flxrs.dankchat.utils.extensions

import android.util.Log
import kotlinx.coroutines.*

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