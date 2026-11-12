package com.flxrs.dankchat.data.auth

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.chat.ChatConnector
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.di.DispatchersProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class AuthStateCoordinatorTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val immediate: CoroutineDispatcher = testDispatcher
        }
    private val channels = persistentListOf("forsen".toUserName())
    private val readableSettings = MutableStateFlow(AuthSettings())

    // Only the state that credentials are read from is stubbed, any other source fails the test
    private val authDataStore: AuthDataStore = mockk { every { currentSettings } returns readableSettings }
    private val chatChannelProvider: ChatChannelProvider = mockk { every { channels } returns MutableStateFlow<ImmutableList<UserName>?>(this@AuthStateCoordinatorTest.channels) }
    private val chatConnector: ChatConnector = mockk(relaxed = true)

    @Test
    fun `startup login state does not reconnect`() {
        readableSettings.value = LOGGED_IN
        createCoordinator()

        verify(exactly = 0) { chatConnector.closeAndReconnect(any()) }
    }

    @Test
    fun `login reconnects when the readable credentials change`() {
        createCoordinator()

        readableSettings.value = LOGGED_IN

        verify(exactly = 1) { chatConnector.closeAndReconnect(channels) }
    }

    @Test
    fun `logout reconnects when the readable credentials change`() {
        readableSettings.value = LOGGED_IN
        createCoordinator()

        readableSettings.value = AuthSettings()

        verify(exactly = 1) { chatConnector.closeAndReconnect(channels) }
    }

    private fun createCoordinator() = AuthStateCoordinator(
        authDataStore = authDataStore,
        chatChannelProvider = chatChannelProvider,
        chatConnector = chatConnector,
        channelDataCoordinator = mockk(relaxed = true),
        emoteRepository = mockk(relaxed = true),
        authApiClient = mockk(),
        ignoresRepository = mockk(relaxed = true),
        userStateRepository = mockk(relaxed = true),
        emoteUsageRepository = mockk(relaxed = true),
        startupValidationHolder = StartupValidationHolder(),
        chatEventProcessor = mockk(relaxed = true),
        chatNotificationRepository = mockk(relaxed = true),
        sendWaitRepository = mockk(relaxed = true),
        usersRepository = mockk(relaxed = true),
        dispatchersProvider = dispatchersProvider,
    )

    private companion object {
        val LOGGED_IN = AuthSettings(oAuthKey = "oauth:token", userName = "forsen", userId = "1", isLoggedIn = true)
    }
}
