package com.flxrs.dankchat.ui.main

import app.cash.turbine.test
import com.flxrs.dankchat.data.repo.chat.UserStateRepository
import com.flxrs.dankchat.data.repo.data.DataLoadingFailure
import com.flxrs.dankchat.data.repo.data.DataLoadingStep
import com.flxrs.dankchat.data.state.GlobalLoadingState
import com.flxrs.dankchat.domain.ChannelDataCoordinator
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettings
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.developer.DeveloperSettings
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import com.flxrs.dankchat.preferences.stream.StreamsSettingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
internal class LoadingFailureStateTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val globalLoadingStateFlow = MutableStateFlow<GlobalLoadingState>(GlobalLoadingState.Idle)

    private val channelDataCoordinator: ChannelDataCoordinator = mockk()
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore = mockk()
    private val preferenceStore: DankChatPreferenceStore = mockk()
    private val developerSettingsDataStore: DeveloperSettingsDataStore = mockk()
    private val streamsSettingsDataStore: StreamsSettingsDataStore = mockk()
    private val userStateRepository: UserStateRepository = mockk()

    private lateinit var viewModel: MainScreenViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { channelDataCoordinator.globalLoadingState } returns globalLoadingStateFlow
        justRun { channelDataCoordinator.loadGlobalData() }

        every { appearanceSettingsDataStore.settings } returns MutableStateFlow(AppearanceSettings())
        coEvery { appearanceSettingsDataStore.update(any()) } returns Unit
        every { developerSettingsDataStore.settings } returns MutableStateFlow(DeveloperSettings())
        every { streamsSettingsDataStore.keepStreamWhileTyping } returns MutableStateFlow(false)
        every { preferenceStore.keyboardHeightPortrait } returns 0
        every { preferenceStore.keyboardHeightLandscape } returns 0

        viewModel = MainScreenViewModel(
            channelDataCoordinator = channelDataCoordinator,
            appearanceSettingsDataStore = appearanceSettingsDataStore,
            preferenceStore = preferenceStore,
            developerSettingsDataStore = developerSettingsDataStore,
            userStateRepository = userStateRepository,
            streamsSettingsDataStore = streamsSettingsDataStore,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has no failure`() = runTest(testDispatcher) {
        viewModel.loadingFailureState.test {
            val state = awaitItem()
            assertNull(state.failure)
            assertFalse(state.acknowledged)
        }
    }

    @Test
    fun `failure is emitted when loading fails`() = runTest(testDispatcher) {
        viewModel.loadingFailureState.test {
            skipItems(1) // initial

            globalLoadingStateFlow.value = FAILURE_1
            val state = awaitItem()
            assertEquals(FAILURE_1, state.failure)
            assertFalse(state.acknowledged)
        }
    }

    @Test
    fun `acknowledged failure is marked as such`() = runTest(testDispatcher) {
        viewModel.loadingFailureState.test {
            skipItems(1)

            globalLoadingStateFlow.value = FAILURE_1
            skipItems(1) // unacknowledged emission

            viewModel.acknowledgeFailure(FAILURE_1)
            val state = awaitItem()
            assertEquals(FAILURE_1, state.failure)
            assertTrue(state.acknowledged)
        }
    }

    @Test
    fun `acknowledged failure does not re-emit as unacknowledged`() = runTest(testDispatcher) {
        viewModel.loadingFailureState.test {
            skipItems(1)

            globalLoadingStateFlow.value = FAILURE_1
            skipItems(1)

            viewModel.acknowledgeFailure(FAILURE_1)
            val state = awaitItem()
            assertTrue(state.acknowledged)

            // Same failure value re-emitted — should stay acknowledged
            expectNoEvents()
        }
    }

    @Test
    fun `new different failure is unacknowledged`() = runTest(testDispatcher) {
        viewModel.loadingFailureState.test {
            skipItems(1)

            globalLoadingStateFlow.value = FAILURE_1
            skipItems(1)
            viewModel.acknowledgeFailure(FAILURE_1)
            skipItems(1)

            globalLoadingStateFlow.value = FAILURE_2
            val state = awaitItem()
            assertEquals(FAILURE_2, state.failure)
            assertFalse(state.acknowledged)
        }
    }

    @Test
    fun `loading state clears failure`() = runTest(testDispatcher) {
        viewModel.loadingFailureState.test {
            skipItems(1)

            globalLoadingStateFlow.value = FAILURE_1
            skipItems(1)
            viewModel.acknowledgeFailure(FAILURE_1)
            skipItems(1)

            globalLoadingStateFlow.value = GlobalLoadingState.Loading
            val state = awaitItem()
            assertNull(state.failure)
            assertFalse(state.acknowledged)
        }
    }

    @Test
    fun `retry resets acknowledged so same failure can re-show`() = runTest(testDispatcher) {
        every { channelDataCoordinator.retryDataLoading(any()) } answers {
            globalLoadingStateFlow.value = GlobalLoadingState.Loading
        }

        viewModel.loadingFailureState.test {
            skipItems(1)

            globalLoadingStateFlow.value = FAILURE_1
            skipItems(1)
            viewModel.acknowledgeFailure(FAILURE_1)
            skipItems(1)

            // Retry triggers Loading → acknowledged is cleared reactively
            viewModel.retryDataLoading(FAILURE_1)
            val loadingState = awaitItem()
            assertNull(loadingState.failure)
            assertFalse(loadingState.acknowledged)

            // Same failure comes back after retry — should be unacknowledged
            globalLoadingStateFlow.value = FAILURE_1
            val state = awaitItem()
            assertEquals(FAILURE_1, state.failure)
            assertFalse(state.acknowledged)
        }
    }

    @Test
    fun `loaded state clears failure`() = runTest(testDispatcher) {
        viewModel.loadingFailureState.test {
            skipItems(1)

            globalLoadingStateFlow.value = FAILURE_1
            skipItems(1)

            globalLoadingStateFlow.value = GlobalLoadingState.Loaded
            val state = awaitItem()
            assertNull(state.failure)
        }
    }

    companion object {
        private val FAILURE_1 = GlobalLoadingState.Failed(
            failures = setOf(DataLoadingFailure(DataLoadingStep.DankChatBadges, RuntimeException("test"))),
        )
        private val FAILURE_2 = GlobalLoadingState.Failed(
            failures = setOf(DataLoadingFailure(DataLoadingStep.GlobalBadges, RuntimeException("test"))),
        )
    }
}
