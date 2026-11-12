package com.flxrs.dankchat.domain

import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.IgnoresRepository
import io.mockk.awaits
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class UserBlocksGateTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val authDataStore: AuthDataStore = mockk()
    private val ignoresRepository: IgnoresRepository = mockk(relaxed = true)
    private val gate = UserBlocksGate(authDataStore, ignoresRepository)

    @Test
    fun `finished load opens the gate`() = runTest(testDispatcher) {
        every { authDataStore.isLoggedIn } returns true
        gate.close()

        gate.loadAndOpen()

        assertTrue(launch { gate.awaitReady() }.isCompleted)
    }

    @Test
    fun `cancelled load leaves the gate closed for the load that replaces it`() = runTest(testDispatcher) {
        every { authDataStore.isLoggedIn } returns true
        coEvery { ignoresRepository.loadUserBlocks() } just awaits
        gate.close()

        launch { gate.loadAndOpen() }.cancelAndJoin()

        val waiting = launch { gate.awaitReady() }
        assertFalse(waiting.isCompleted)
        waiting.cancelAndJoin()
    }
}
