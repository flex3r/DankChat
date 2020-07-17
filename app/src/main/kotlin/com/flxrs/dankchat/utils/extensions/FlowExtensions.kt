package com.flxrs.dankchat.utils.extensions

import com.flxrs.dankchat.utils.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

fun <T> Flow<Event<T>>.onEachEvent(action: suspend (T) -> Unit): Flow<T> = transform { value ->
    value.getContentIfNotHandled()?.let {
        action(it)
        emit(it)
    }
}