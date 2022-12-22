package com.flxrs.dankchat.data.repo.data

sealed class ServiceEvent {
    object Shutdown : ServiceEvent()
}