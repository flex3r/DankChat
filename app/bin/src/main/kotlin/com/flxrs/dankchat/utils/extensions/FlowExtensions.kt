package com.flxrs.dankchat.utils.extensions

import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

inline fun <T> Fragment.collectFlow(flow: Flow<T>, crossinline action: (T) -> Unit) {
    flow.flowWithLifecycle(lifecycle)
        .onEach { action(it) }
        .launchIn(lifecycleScope)
}

fun <T> mutableSharedFlowOf(
    defaultValue: T,
    replayValue: Int = 1,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST
): MutableSharedFlow<T> = MutableSharedFlow<T>(replayValue, extraBufferCapacity, onBufferOverflow).apply {
    tryEmit(defaultValue)
}