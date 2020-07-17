package com.flxrs.dankchat.utils

open class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set // Allow external read but not write

    fun getContentIfNotHandled(): T? {
        return when {
            hasBeenHandled -> null
            else -> content.also { hasBeenHandled = true }
        }
    }

    fun peekContent(): T = content
}