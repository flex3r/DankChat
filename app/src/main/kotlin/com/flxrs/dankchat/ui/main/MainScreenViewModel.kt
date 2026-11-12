package com.flxrs.dankchat.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.chat.UserStateRepository
import com.flxrs.dankchat.data.state.GlobalLoadingState
import com.flxrs.dankchat.domain.ChannelDataCoordinator
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.appearance.InputAction
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import com.flxrs.dankchat.preferences.stream.StreamsSettingsDataStore
import com.flxrs.dankchat.preferences.tools.ToolsSettingsDataStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class MainScreenViewModel(
    private val channelDataCoordinator: ChannelDataCoordinator,
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore,
    private val preferenceStore: DankChatPreferenceStore,
    private val developerSettingsDataStore: DeveloperSettingsDataStore,
    private val userStateRepository: UserStateRepository,
    private val toolsSettingsDataStore: ToolsSettingsDataStore,
    streamsSettingsDataStore: StreamsSettingsDataStore,
) : ViewModel() {
    private val _loadingFailureState = MutableStateFlow(LoadingFailureState())
    val loadingFailureState: StateFlow<LoadingFailureState> = _loadingFailureState.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    private val _gestureToolbarHidden = MutableStateFlow(false)
    private val _keyboardHeightUpdates = MutableSharedFlow<KeyboardHeightUpdate>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _keyboardHeightPx = MutableStateFlow(0)
    val keyboardHeightPx: StateFlow<Int> = _keyboardHeightPx.asStateFlow()

    private val screenModes = combine(_isFullscreen, _gestureToolbarHidden, ::Pair)

    val uiState: StateFlow<MainScreenUiState> =
        combine(
            appearanceSettingsDataStore.settings,
            developerSettingsDataStore.settings,
            streamsSettingsDataStore.keepStreamWhileTyping,
            toolsSettingsDataStore.ttsEnabled,
            screenModes,
        ) { appearance, developerSettings, keepStreamWhileTyping, ttsEnabled, (isFullscreen, gestureToolbarHidden) ->
            MainScreenUiState(
                isFullscreen = isFullscreen,
                isTTSEnabled = ttsEnabled,
                showInput = appearance.showInput,
                inputActions = appearance.inputActions.toImmutableList(),
                showCharacterCounter = appearance.showCharacterCounter,
                isRepeatedSendEnabled = developerSettings.repeatedSending,
                debugMode = developerSettings.debugMode,
                fontSize = appearance.fontSize,
                keepStreamWhileTyping = keepStreamWhileTyping,
                swipeNavigation = appearance.swipeNavigation,
                gestureToolbarHidden = gestureToolbarHidden,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState())

    init {
        channelDataCoordinator.loadGlobalData()

        viewModelScope.launch {
            channelDataCoordinator.globalLoadingState.collect { state ->
                val failure = state as? GlobalLoadingState.Failed
                _loadingFailureState.update { current ->
                    when (failure) {
                        null -> LoadingFailureState()
                        current.failure if current.acknowledged -> current
                        else -> LoadingFailureState(failure = failure)
                    }
                }
            }
        }

        viewModelScope.launch {
            var previousDebugMode: Boolean? = null
            developerSettingsDataStore.settings
                .map { it.debugMode }
                .distinctUntilChanged()
                .collect { enabled ->
                    val wasEnabled = previousDebugMode
                    previousDebugMode = enabled

                    appearanceSettingsDataStore.update { appearance ->
                        val actions = appearance.inputActions
                        when {
                            // Only auto-add on actual false→true toggle, not on cold start
                            enabled && wasEnabled == false && InputAction.Debug !in actions && actions.size < MAX_INPUT_ACTIONS -> {
                                appearance.copy(inputActions = actions + InputAction.Debug)
                            }

                            !enabled && InputAction.Debug in actions -> {
                                appearance.copy(inputActions = actions - InputAction.Debug)
                            }

                            else -> {
                                appearance
                            }
                        }
                    }
                }
        }

        // The emote menu matches the keyboard without its panels (emoji, clipboard), which only make it taller.
        // So the height it opens with is kept, and while it stays open only a lower height replaces it.
        // Every height has to hold for the settle time, heights in passing are skipped
        viewModelScope.launch {
            var settled: KeyboardHeightUpdate? = null
            _keyboardHeightUpdates
                .distinctUntilChanged()
                .collectLatest { update ->
                    val current = settled
                    when {
                        update.heightPx == 0 -> {
                            settled = null
                        }

                        current == null || current.isLandscape != update.isLandscape || update.heightPx < current.heightPx -> {
                            delay(KEYBOARD_SETTLE_MS)
                            settled = update
                            _keyboardHeightPx.value = update.heightPx
                            when {
                                update.isLandscape -> preferenceStore.keyboardHeightLandscape = update.heightPx
                                else -> preferenceStore.keyboardHeightPortrait = update.heightPx
                            }
                        }
                    }
                }
        }
    }

    fun isModeratorInChannel(channel: UserName?): Boolean = userStateRepository.isModeratorInChannel(channel)

    fun setGestureToolbarHidden(hidden: Boolean) {
        _gestureToolbarHidden.value = hidden
    }

    fun hideInput() {
        viewModelScope.launch {
            appearanceSettingsDataStore.update { it.copy(showInput = false) }
        }
    }

    fun initKeyboardHeight(isLandscape: Boolean) {
        val persisted = if (isLandscape) preferenceStore.keyboardHeightLandscape else preferenceStore.keyboardHeightPortrait
        _keyboardHeightPx.value = persisted
    }

    fun trackKeyboardHeight(
        heightPx: Int,
        isLandscape: Boolean,
        minHeightPx: Float,
    ) {
        // Anything below the minimum counts as a closed keyboard
        val openHeightPx =
            when {
                heightPx > minHeightPx -> heightPx
                else -> 0
            }
        _keyboardHeightUpdates.tryEmit(KeyboardHeightUpdate(openHeightPx, isLandscape))
    }

    fun toggleInput() {
        viewModelScope.launch {
            appearanceSettingsDataStore.update { it.copy(showInput = !it.showInput) }
        }
    }

    fun recoverInputAndFullscreen() {
        _isFullscreen.value = false
        _gestureToolbarHidden.value = false
        viewModelScope.launch {
            appearanceSettingsDataStore.update { it.copy(showInput = true) }
        }
    }

    fun showInput() {
        _gestureToolbarHidden.value = false
        viewModelScope.launch {
            appearanceSettingsDataStore.update { it.copy(showInput = true) }
        }
    }

    fun exitFullscreen() {
        _gestureToolbarHidden.value = false
        _isFullscreen.value = false
    }

    fun updateInputActions(actions: ImmutableList<InputAction>) {
        viewModelScope.launch {
            appearanceSettingsDataStore.update { it.copy(inputActions = actions) }
        }
    }

    fun toggleFullscreen() {
        _isFullscreen.update { !it }
    }

    fun setTTSEnabled(enabled: Boolean) {
        viewModelScope.launch {
            toolsSettingsDataStore.update { it.copy(ttsEnabled = enabled) }
        }
    }

    fun acknowledgeFailure(failure: GlobalLoadingState.Failed) {
        _loadingFailureState.update { current ->
            if (current.failure == failure) current.copy(acknowledged = true) else current
        }
    }

    fun retryDataLoading(failedState: GlobalLoadingState.Failed) {
        channelDataCoordinator.retryDataLoading(failedState)
    }

    companion object {
        private const val MAX_INPUT_ACTIONS = 4
        private const val KEYBOARD_SETTLE_MS = 300L
    }
}

data class LoadingFailureState(
    val failure: GlobalLoadingState.Failed? = null,
    val acknowledged: Boolean = false,
)

private data class KeyboardHeightUpdate(
    val heightPx: Int,
    val isLandscape: Boolean,
)
