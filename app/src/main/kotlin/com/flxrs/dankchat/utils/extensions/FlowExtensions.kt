package com.flxrs.dankchat.utils.extensions

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

inline fun <T> Fragment.collectFlow(flow: Flow<T>, crossinline action: (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}

fun <T> mutableSharedFlowOf(
    defaultValue: T,
    replayValue: Int = 1,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST
): MutableSharedFlow<T> = MutableSharedFlow<T>(replayValue, extraBufferCapacity, onBufferOverflow).apply {
    tryEmit(defaultValue)
}

inline fun <T, R> Flow<T?>.flatMapLatestOrDefault(defaultValue: R, crossinline transform: suspend (value: T) -> Flow<R>): Flow<R> =
    transformLatest {
        when (it) {
            null -> emit(defaultValue)
            else -> emitAll(transform(it))
        }
    }