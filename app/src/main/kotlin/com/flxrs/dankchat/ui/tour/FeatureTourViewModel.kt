package com.flxrs.dankchat.ui.tour

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.auth.StartupValidation
import com.flxrs.dankchat.data.auth.StartupValidationHolder
import com.flxrs.dankchat.ui.onboarding.OnboardingDataStore
import com.flxrs.dankchat.ui.onboarding.OnboardingSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

const val CURRENT_TOUR_VERSION = 1

enum class TourStep {
    InputActions,
    OverflowMenu,
    ConfigureActions,
    SwipeGesture,
    RecoveryFab,
}

@Immutable
sealed interface PostOnboardingStep {
    /** Waiting for conditions (onboarding not done yet, or no channels). */
    data object Idle : PostOnboardingStep

    /** Show tooltip on the toolbar plus icon. */
    data object ToolbarPlusHint : PostOnboardingStep

    /** Run the feature tour. */
    data object FeatureTour : PostOnboardingStep

    /** Everything done. */
    data object Complete : PostOnboardingStep
}

@Immutable
data class FeatureTourUiState(
    val postOnboardingStep: PostOnboardingStep = PostOnboardingStep.Idle,
    val currentTourStep: TourStep? = null,
    val isTourActive: Boolean = false,
    val forceOverflowOpen: Boolean = false,
    val gestureInputHidden: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@KoinViewModel
class FeatureTourViewModel(
    private val onboardingDataStore: OnboardingDataStore,
    startupValidationHolder: StartupValidationHolder,
) : ViewModel() {
    // Material3 tooltip states
    val inputActionsTooltipState = TooltipState(isPersistent = true)
    val overflowMenuTooltipState = TooltipState(isPersistent = true)
    val configureActionsTooltipState = TooltipState(isPersistent = true)
    val swipeGestureTooltipState = TooltipState(isPersistent = true)
    val recoveryFabTooltipState = TooltipState(isPersistent = true)
    val addChannelTooltipState = TooltipState(isPersistent = true)

    private data class TourInternalState(
        val isActive: Boolean = false,
        val stepIndex: Int = 0,
        val forceOverflowOpen: Boolean = false,
        val gestureInputHidden: Boolean = false,
        val completed: Boolean = false,
    )

    private data class ChannelState(
        val ready: Boolean = false,
        val empty: Boolean = true,
    )

    private enum class ToolbarHintState {
        Pending,
        Showing,
        Done,
    }

    private val _tourState = MutableStateFlow(TourInternalState())
    private val _channelState = MutableStateFlow(ChannelState())
    private val _toolbarHintState = MutableStateFlow(ToolbarHintState.Pending)

    val uiState: StateFlow<FeatureTourUiState> =
        combine(
            onboardingDataStore.settings,
            _tourState,
            _channelState,
            _toolbarHintState,
            startupValidationHolder.state,
        ) { settings, tour, channel, toolbarHint, validation ->
            val currentStep =
                when {
                    !tour.isActive -> null
                    tour.stepIndex >= TourStep.entries.size -> null
                    else -> TourStep.entries[tour.stepIndex]
                }
            FeatureTourUiState(
                postOnboardingStep =
                    resolvePostOnboardingStep(
                        settings = settings,
                        channelReady = channel.ready,
                        channelEmpty = channel.empty,
                        // The hint is stored as shown while it is still up, so only a hint that is not up counts as shown before
                        toolbarHintDone = toolbarHint == ToolbarHintState.Done || (toolbarHint == ToolbarHintState.Pending && settings.hasShownToolbarHint),
                        tourActive = tour.isActive,
                        tourCompleted = tour.completed,
                        authValidated = validation is StartupValidation.Validated,
                    ),
                currentTourStep = currentStep,
                isTourActive = tour.isActive,
                forceOverflowOpen = tour.forceOverflowOpen,
                gestureInputHidden = tour.gestureInputHidden,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeatureTourUiState())

    // -- Channel state updates from MainScreen --

    fun onChannelsChanged(
        empty: Boolean,
        ready: Boolean,
    ) {
        _channelState.value = ChannelState(ready = ready, empty = empty)
    }

    /** The hint counts as shown from the moment it is up, a restart does not bring it back. */
    fun onToolbarHintShown() {
        if (_toolbarHintState.value != ToolbarHintState.Pending) {
            return
        }
        _toolbarHintState.value = ToolbarHintState.Showing
        viewModelScope.launch {
            onboardingDataStore.update { it.copy(hasShownToolbarHint = true) }
        }
    }

    /** The hint was dismissed, or the toolbar + icon was used and the hint is not needed. */
    fun onToolbarHintDone() {
        if (_toolbarHintState.value == ToolbarHintState.Done) {
            return
        }
        _toolbarHintState.value = ToolbarHintState.Done
        viewModelScope.launch {
            onboardingDataStore.update { it.copy(hasShownToolbarHint = true) }
        }
    }

    fun startTour() {
        val tour = _tourState.value
        if (tour.isActive || tour.completed) return
        // The tour counts as shown from its first tooltip on, a restart does not bring it back
        viewModelScope.launch {
            onboardingDataStore.update { it.copy(featureTourVersion = CURRENT_TOUR_VERSION, hasShownToolbarHint = true) }
        }
        _tourState.value = TourInternalState(isActive = true)
        showTooltipForStep(TourStep.entries.first())
    }

    fun advance() {
        val tour = _tourState.value
        if (!tour.isActive) return
        val currentStep = TourStep.entries.getOrNull(tour.stepIndex) ?: return

        // Skip dismiss for ConfigureActions — its tooltip is inside the menu popup,
        // so removing the composable (via step change) handles cleanup.
        // Explicit dismiss() causes a popup exit animation that flashes.
        if (currentStep != TourStep.ConfigureActions) {
            tooltipStateForStep(currentStep).dismiss()
        }

        val nextIndex = tour.stepIndex + 1
        val nextStep = TourStep.entries.getOrNull(nextIndex)
        when {
            nextStep == null -> {
                completeTour()
            }

            else -> {
                _tourState.update {
                    it.copy(
                        stepIndex = nextIndex,
                        forceOverflowOpen = nextStep == TourStep.ConfigureActions,
                        gestureInputHidden = nextStep == TourStep.RecoveryFab,
                    )
                }
                // Menu close animation takes ~150ms; show the next tooltip after it finishes
                if (currentStep == TourStep.ConfigureActions) {
                    viewModelScope.launch {
                        delay(250)
                        showTooltipForStep(nextStep)
                    }
                } else {
                    showTooltipForStep(nextStep)
                }
            }
        }
    }

    fun skipTour() {
        val tour = _tourState.value
        if (tour.isActive) {
            val currentStep = TourStep.entries.getOrNull(tour.stepIndex)
            currentStep?.let { tooltipStateForStep(it).dismiss() }
        }
        completeTour()
    }

    private fun completeTour() {
        _tourState.value = TourInternalState(completed = true)
        _toolbarHintState.value = ToolbarHintState.Done
        viewModelScope.launch {
            onboardingDataStore.update {
                it.copy(
                    featureTourVersion = CURRENT_TOUR_VERSION,
                    hasShownToolbarHint = true,
                )
            }
        }
    }

    private fun showTooltipForStep(step: TourStep) {
        viewModelScope.launch { tooltipStateForStep(step).show() }
    }

    private fun tooltipStateForStep(step: TourStep): TooltipState = when (step) {
        TourStep.InputActions -> inputActionsTooltipState
        TourStep.OverflowMenu -> overflowMenuTooltipState
        TourStep.ConfigureActions -> configureActionsTooltipState
        TourStep.SwipeGesture -> swipeGestureTooltipState
        TourStep.RecoveryFab -> recoveryFabTooltipState
    }

    private fun resolvePostOnboardingStep(
        settings: OnboardingSettings,
        channelReady: Boolean,
        channelEmpty: Boolean,
        toolbarHintDone: Boolean,
        tourActive: Boolean,
        tourCompleted: Boolean,
        authValidated: Boolean,
    ): PostOnboardingStep = when {
        tourCompleted -> PostOnboardingStep.Complete

        // A running tour is already stored as shown, so it has to win over the stored version
        tourActive -> PostOnboardingStep.FeatureTour

        settings.featureTourVersion >= CURRENT_TOUR_VERSION && toolbarHintDone -> PostOnboardingStep.Complete

        !settings.hasCompletedOnboarding -> PostOnboardingStep.Idle

        !authValidated -> PostOnboardingStep.Idle

        !channelReady -> PostOnboardingStep.Idle

        channelEmpty -> PostOnboardingStep.Idle

        !toolbarHintDone -> PostOnboardingStep.ToolbarPlusHint

        // At this point: toolbarHintDone=true but (version >= CURRENT && toolbarHintDone) was false,
        // so featureTourVersion < CURRENT_TOUR_VERSION is guaranteed.
        else -> PostOnboardingStep.FeatureTour
    }
}
