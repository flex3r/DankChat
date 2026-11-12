package com.flxrs.dankchat.ui.tour

import app.cash.turbine.test
import com.flxrs.dankchat.data.auth.StartupValidation
import com.flxrs.dankchat.data.auth.StartupValidationHolder
import com.flxrs.dankchat.ui.onboarding.OnboardingDataStore
import com.flxrs.dankchat.ui.onboarding.OnboardingSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.junit5.MockKExtension
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
internal class FeatureTourViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val settingsFlow = MutableStateFlow(OnboardingSettings())
    private val onboardingDataStore: OnboardingDataStore = mockk()

    private lateinit var viewModel: FeatureTourViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { onboardingDataStore.settings } returns settingsFlow
        every { onboardingDataStore.current() } answers { settingsFlow.value }

        coEvery { onboardingDataStore.update(any()) } coAnswers {
            val transform = firstArg<suspend (OnboardingSettings) -> OnboardingSettings>()
            settingsFlow.value = transform(settingsFlow.value)
            Result.success(settingsFlow.value)
        }

        val startupValidationHolder = StartupValidationHolder().apply { update(StartupValidation.Validated) }
        viewModel = FeatureTourViewModel(onboardingDataStore, startupValidationHolder)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Post-onboarding step resolution --

    @Test
    fun `initial state is Idle when onboarding not completed`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            assertEquals(PostOnboardingStep.Idle, awaitItem().postOnboardingStep)
        }
    }

    @Test
    fun `step is Idle when onboarding complete but channels empty`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            assertEquals(PostOnboardingStep.Idle, awaitItem().postOnboardingStep)

            emitSettings { it.copy(hasCompletedOnboarding = true) }
            viewModel.onChannelsChanged(empty = true, ready = true)

            // State stays Idle — StateFlow deduplicates, no new emission
            expectNoEvents()
        }
    }

    @Test
    fun `step is Idle when channels not ready`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            assertEquals(PostOnboardingStep.Idle, awaitItem().postOnboardingStep)

            emitSettings { it.copy(hasCompletedOnboarding = true) }
            viewModel.onChannelsChanged(empty = false, ready = false)

            // State stays Idle — StateFlow deduplicates, no new emission
            expectNoEvents()
        }
    }

    @Test
    fun `step is ToolbarPlusHint when onboarding complete and channels ready`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            emitSettings { it.copy(hasCompletedOnboarding = true) }
            viewModel.onChannelsChanged(empty = false, ready = true)

            assertEquals(PostOnboardingStep.ToolbarPlusHint, expectMostRecentItem().postOnboardingStep)
        }
    }

    @Test
    fun `step is FeatureTour after toolbar hint dismissed`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            emitSettings { it.copy(hasCompletedOnboarding = true) }
            viewModel.onChannelsChanged(empty = false, ready = true)
            viewModel.onToolbarHintDismissed()

            assertEquals(PostOnboardingStep.FeatureTour, expectMostRecentItem().postOnboardingStep)
        }
    }

    @Test
    fun `step is Complete when tour version is current and toolbar hint done`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            emitSettings {
                it.copy(
                    hasCompletedOnboarding = true,
                    hasShownToolbarHint = true,
                    featureTourVersion = CURRENT_TOUR_VERSION,
                )
            }
            viewModel.onChannelsChanged(empty = false, ready = true)

            assertEquals(PostOnboardingStep.Complete, expectMostRecentItem().postOnboardingStep)
        }
    }

    @Test
    fun `existing user migration skips toolbar hint but shows tour`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            emitSettings {
                it.copy(
                    hasCompletedOnboarding = true,
                    hasShownToolbarHint = true,
                    featureTourVersion = 0,
                )
            }
            viewModel.onChannelsChanged(empty = false, ready = true)

            assertEquals(PostOnboardingStep.FeatureTour, expectMostRecentItem().postOnboardingStep)
        }
    }

    // -- Tour lifecycle --

    @Test
    fun `startTour activates tour at first step`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            setupAndStartTour()

            val state = expectMostRecentItem()
            assertTrue(state.isTourActive)
            assertEquals(TourStep.InputActions, state.currentTourStep)
        }
    }

    @Test
    fun `startTour is idempotent when already active`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            setupAndStartTour()
            viewModel.advance() // move to OverflowMenu
            viewModel.startTour() // should be no-op

            assertEquals(TourStep.OverflowMenu, expectMostRecentItem().currentTourStep)
        }
    }

    @Test
    fun `advance progresses through all steps in order`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            setupAndStartTour()
            assertEquals(TourStep.InputActions, expectMostRecentItem().currentTourStep)

            viewModel.advance()
            assertEquals(TourStep.OverflowMenu, expectMostRecentItem().currentTourStep)

            viewModel.advance()
            assertEquals(TourStep.ConfigureActions, expectMostRecentItem().currentTourStep)

            viewModel.advance()
            assertEquals(TourStep.SwipeGesture, expectMostRecentItem().currentTourStep)

            viewModel.advance()
            assertEquals(TourStep.RecoveryFab, expectMostRecentItem().currentTourStep)
        }
    }

    @Test
    fun `advance past last step completes tour`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            setupAndStartTour()
            repeat(TourStep.entries.size) { viewModel.advance() }

            val state = expectMostRecentItem()
            assertFalse(state.isTourActive)
            assertNull(state.currentTourStep)
            assertEquals(PostOnboardingStep.Complete, state.postOnboardingStep)
        }
    }

    @Test
    fun `skipTour completes immediately`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            setupAndStartTour()
            viewModel.advance() // at OverflowMenu

            viewModel.skipTour()

            val state = expectMostRecentItem()
            assertFalse(state.isTourActive)
            assertNull(state.currentTourStep)
            assertEquals(PostOnboardingStep.Complete, state.postOnboardingStep)
        }
    }

    @Test
    fun `startTour after completion is no-op`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            setupAndStartTour()
            repeat(TourStep.entries.size) { viewModel.advance() }

            viewModel.startTour()

            assertFalse(expectMostRecentItem().isTourActive)
        }
    }

    // -- Persistence --

    @Test
    fun `completeTour persists tour version and clears step`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            setupAndStartTour()
            repeat(TourStep.entries.size) { viewModel.advance() }
            cancelAndIgnoreRemainingEvents()
        }

        val persisted = settingsFlow.value
        assertEquals(CURRENT_TOUR_VERSION, persisted.featureTourVersion)
        assertEquals(0, persisted.featureTourStep)
        assertTrue(persisted.hasShownToolbarHint)
    }

    @Test
    fun `advance persists current step index`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            setupAndStartTour()
            viewModel.advance() // step 1
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, settingsFlow.value.featureTourStep)
    }

    @Test
    fun `skipTour before tour starts completes everything`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            emitSettings { it.copy(hasCompletedOnboarding = true) }
            viewModel.onChannelsChanged(empty = false, ready = true)
            // Currently at ToolbarPlusHint

            viewModel.skipTour()

            assertEquals(PostOnboardingStep.Complete, expectMostRecentItem().postOnboardingStep)
        }

        val persisted = settingsFlow.value
        assertEquals(CURRENT_TOUR_VERSION, persisted.featureTourVersion)
        assertTrue(persisted.hasShownToolbarHint)
    }

    // -- Toolbar hint --

    @Test
    fun `onToolbarHintDismissed is idempotent`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            emitSettings { it.copy(hasCompletedOnboarding = true) }
            viewModel.onChannelsChanged(empty = false, ready = true)

            viewModel.onToolbarHintDismissed()
            viewModel.onToolbarHintDismissed() // second call should be no-op

            assertEquals(PostOnboardingStep.FeatureTour, expectMostRecentItem().postOnboardingStep)
        }
    }

    @Test
    fun `onAddedChannelFromToolbar marks hint done`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            emitSettings { it.copy(hasCompletedOnboarding = true) }
            viewModel.onChannelsChanged(empty = false, ready = true)

            viewModel.onAddedChannelFromToolbar()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(settingsFlow.value.hasShownToolbarHint)
    }

    // -- Side effects --

    @Test
    fun `ConfigureActions step forces overflow open`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            setupAndStartTour()
            viewModel.advance() // OverflowMenu
            viewModel.advance() // ConfigureActions

            assertTrue(expectMostRecentItem().forceOverflowOpen)
        }
    }

    @Test
    fun `SwipeGesture step clears forceOverflowOpen`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            setupAndStartTour()
            viewModel.advance() // OverflowMenu
            viewModel.advance() // ConfigureActions
            viewModel.advance() // SwipeGesture

            assertFalse(expectMostRecentItem().forceOverflowOpen)
        }
    }

    @Test
    fun `RecoveryFab step sets gestureInputHidden`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            setupAndStartTour()
            viewModel.advance() // OverflowMenu
            viewModel.advance() // ConfigureActions
            viewModel.advance() // SwipeGesture
            viewModel.advance() // RecoveryFab

            assertTrue(expectMostRecentItem().gestureInputHidden)
        }
    }

    @Test
    fun `tour completion clears gestureInputHidden`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            setupAndStartTour()
            repeat(TourStep.entries.size) { viewModel.advance() }

            assertFalse(expectMostRecentItem().gestureInputHidden)
        }
    }

    // -- Resume --

    @Test
    fun `tour resumes at persisted step with correct side effects`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            emitSettings {
                it.copy(
                    hasCompletedOnboarding = true,
                    hasShownToolbarHint = true,
                    featureTourVersion = 0,
                    featureTourStep = 2,
                )
            }
            viewModel.onChannelsChanged(empty = false, ready = true)

            viewModel.startTour()

            val state = expectMostRecentItem()
            assertEquals(TourStep.ConfigureActions, state.currentTourStep)
            assertTrue(state.forceOverflowOpen)
        }
    }

    @Test
    fun `stale persisted step is ignored when version gap is too large`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            emitSettings {
                it.copy(
                    hasCompletedOnboarding = true,
                    hasShownToolbarHint = true,
                    featureTourVersion = -1, // gap of 2
                    featureTourStep = 3,
                )
            }
            viewModel.onChannelsChanged(empty = false, ready = true)

            viewModel.startTour()

            assertEquals(TourStep.InputActions, expectMostRecentItem().currentTourStep)
        }
    }

    @Test
    fun `resume at RecoveryFab step sets gestureInputHidden`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            skipItems(1)
            emitSettings {
                it.copy(
                    hasCompletedOnboarding = true,
                    hasShownToolbarHint = true,
                    featureTourVersion = 0,
                    featureTourStep = 4, // RecoveryFab
                )
            }
            viewModel.onChannelsChanged(empty = false, ready = true)

            viewModel.startTour()

            val state = expectMostRecentItem()
            assertEquals(TourStep.RecoveryFab, state.currentTourStep)
            assertTrue(state.gestureInputHidden)
        }
    }

    // -- Helpers --

    private fun setupAndStartTour() {
        emitSettings { it.copy(hasCompletedOnboarding = true) }
        viewModel.onChannelsChanged(empty = false, ready = true)
        viewModel.onToolbarHintDismissed()
        viewModel.startTour()
    }

    private fun emitSettings(transform: (OnboardingSettings) -> OnboardingSettings) {
        settingsFlow.value = transform(settingsFlow.value)
    }
}
