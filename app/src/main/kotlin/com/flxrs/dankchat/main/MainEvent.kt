package com.flxrs.dankchat.main

sealed class MainEvent {
    data class Error(val throwable: Throwable) : MainEvent()
}