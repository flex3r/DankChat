package com.flxrs.dankchat.main

sealed interface MainEvent {
    data class Error(val throwable: Throwable) : MainEvent
}
