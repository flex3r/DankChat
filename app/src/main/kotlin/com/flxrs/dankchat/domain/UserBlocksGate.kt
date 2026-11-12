package com.flxrs.dankchat.domain

import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.IgnoresRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import org.koin.core.annotation.Single

/** Gates work that must not run before the user's Twitch block list is loaded — i.e. recent message history. */
@Single
class UserBlocksGate(
    private val authDataStore: AuthDataStore,
    private val ignoresRepository: IgnoresRepository,
) {
    private val ready = MutableStateFlow(true)

    fun close() {
        ready.value = false
    }

    suspend fun loadAndOpen() {
        try {
            if (authDataStore.isLoggedIn) {
                ignoresRepository.loadUserBlocks()
            }
        } finally {
            // A cancelled load is replaced by a new one, which opens the gate once its blocks are loaded
            if (currentCoroutineContext().isActive) {
                ready.value = true
            }
        }
    }

    suspend fun awaitReady() {
        ready.first { it }
    }
}
