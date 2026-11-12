package com.flxrs.dankchat.preferences.dankchataccount

import com.flxrs.dankchat.data.api.dankchat.DankChatApiClient
import com.flxrs.dankchat.data.api.dankchat.dto.DankChatLoginCodeDto
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.auth.StartupValidation
import com.flxrs.dankchat.data.auth.StartupValidationHolder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class DankChatAccountViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dankChatApiClient: DankChatApiClient = mockk()
    private val authDataStore: AuthDataStore = mockk()

    private lateinit var viewModel: DankChatAccountViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { authDataStore.isLoggedIn } returns false
        coEvery { dankChatApiClient.createLoginCode() } coAnswers { Result.success(DankChatLoginCodeDto(url = ACCOUNT_URL)) }

        viewModel =
            DankChatAccountViewModel(
                dankChatApiClient = dankChatApiClient,
                channelDataCoordinator = mockk(),
                authDataStore = authDataStore,
                startupValidationHolder = StartupValidationHolder().apply { update(StartupValidation.Validated) },
                knownBadgeOwnersDataStore = mockk(),
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `account page is not opened twice while it is opening`() = runTest(testDispatcher) {
        val events = mutableListOf<DankChatAccountEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }

        viewModel.manageAccount()
        viewModel.manageAccount()

        assertTrue(viewModel.state.value.isOpeningAccount)
        assertEquals(expected = listOf<DankChatAccountEvent>(DankChatAccountEvent.OpenUrl(ACCOUNT_URL)), actual = events)
    }

    @Test
    fun `account page can be opened again after it could not be opened`() = runTest(testDispatcher) {
        val events = mutableListOf<DankChatAccountEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        viewModel.manageAccount()

        viewModel.onOpenUrlFailed()
        assertFalse(viewModel.state.value.isOpeningAccount)
        viewModel.manageAccount()

        assertEquals(expected = 2, actual = events.size)
    }

    private companion object {
        const val ACCOUNT_URL = "https://dank.chat/account"
    }
}
