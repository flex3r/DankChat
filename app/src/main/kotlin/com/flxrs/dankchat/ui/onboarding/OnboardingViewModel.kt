package com.flxrs.dankchat.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class OnboardingState(
    val initialPage: Int = 0,
    val currentPage: Int = 0,
    val loginCompleted: Boolean = false,
    val messageHistoryDecided: Boolean = false,
)

@KoinViewModel
class OnboardingViewModel(
    private val onboardingDataStore: OnboardingDataStore,
    private val authDataStore: AuthDataStore,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
    private val chatSettingsDataStore: ChatSettingsDataStore,
) : ViewModel() {
    private val _state: MutableStateFlow<OnboardingState>
    val state: StateFlow<OnboardingState>

    init {
        val savedPage = onboardingDataStore.current().onboardingPage
        val isLoggedIn = authDataStore.isLoggedIn
        _state =
            MutableStateFlow(
                OnboardingState(
                    initialPage = savedPage,
                    currentPage = savedPage,
                    loginCompleted = isLoggedIn,
                    // If we're past the history page, the decision was already made and saved in a previous session
                    messageHistoryDecided = savedPage > 2,
                ),
            )
        state = _state.asStateFlow()

        // Observe auth state changes so we detect login during onboarding
        viewModelScope.launch {
            authDataStore.settings
                .map { it.isLoggedIn }
                .distinctUntilChanged()
                .collect { isLoggedIn ->
                    if (isLoggedIn && !_state.value.loginCompleted) {
                        _state.update { it.copy(loginCompleted = true) }
                    }
                }
        }
    }

    fun setCurrentPage(page: Int) {
        _state.update { it.copy(currentPage = page) }
        viewModelScope.launch {
            onboardingDataStore.update { it.copy(onboardingPage = page) }
        }
    }

    fun onMessageHistoryDecision(enabled: Boolean) {
        _state.update { it.copy(messageHistoryDecided = true) }
        viewModelScope.launch {
            chatSettingsDataStore.update { it.copy(loadMessageHistory = enabled) }
        }
    }

    fun completeOnboarding(onComplete: () -> Unit) {
        dankChatPreferenceStore.hasMessageHistoryAcknowledged = true
        viewModelScope.launch {
            onboardingDataStore.update { it.copy(hasCompletedOnboarding = true, onboardingPage = 0) }
            onComplete()
        }
    }
}
