package com.flxrs.dankchat.data.repo.data

sealed interface ServiceEvent {
    data object Shutdown : ServiceEvent
}
