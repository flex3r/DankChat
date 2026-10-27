package com.flxrs.dankchat.preferences.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.api.auth.AuthApiClient
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.ui.onboarding.OnboardingDataStore
import com.flxrs.dankchat.utils.extensions.withTrailingSlash
import com.flxrs.dankchat.utils.extensions.withoutOAuthPrefix
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class DeveloperSettingsViewModel(
    private val developerSettingsDataStore: DeveloperSettingsDataStore,
    private val onboardingDataStore: OnboardingDataStore,
    private val authApiClient: AuthApiClient,
    private val authDataStore: AuthDataStore,
) : ViewModel() {
    private val initial = developerSettingsDataStore.current()
    val settings =
        developerSettingsDataStore.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds),
            initialValue = initial,
        )

    private val _events = MutableSharedFlow<DeveloperSettingsEvent>()
    val events = _events.asSharedFlow()

    fun onInteraction(interaction: DeveloperSettingsInteraction) = viewModelScope.launch {
        runCatching {
            when (interaction) {
                is DeveloperSettingsInteraction.DebugMode -> {
                    developerSettingsDataStore.update { it.copy(debugMode = interaction.value) }
                }

                is DeveloperSettingsInteraction.RepeatedSending -> {
                    developerSettingsDataStore.update { it.copy(repeatedSending = interaction.value) }
                }

                is DeveloperSettingsInteraction.BypassCommandHandling -> {
                    developerSettingsDataStore.update { it.copy(bypassCommandHandling = interaction.value) }
                }

                is DeveloperSettingsInteraction.CustomRecentMessagesHost -> {
                    val withSlash =
                        interaction.host
                            .ifBlank { DeveloperSettings.RM_HOST_DEFAULT }
                            .withTrailingSlash
                    if (withSlash == developerSettingsDataStore.settings.first().customRecentMessagesHost) return@launch
                    developerSettingsDataStore.update { it.copy(customRecentMessagesHost = withSlash) }
                    _events.emit(DeveloperSettingsEvent.RestartRequired)
                }

                is DeveloperSettingsInteraction.EventSubEnabled -> {
                    developerSettingsDataStore.update { it.copy(eventSubEnabled = interaction.value) }
                    if (initial.eventSubEnabled != interaction.value) {
                        _events.emit(DeveloperSettingsEvent.RestartRequired)
                    }
                }

                is DeveloperSettingsInteraction.EventSubDebugOutput -> {
                    developerSettingsDataStore.update { it.copy(eventSubDebugOutput = interaction.value) }
                }

                is DeveloperSettingsInteraction.EmoteAnimationClock -> {
                    developerSettingsDataStore.update { it.copy(emoteAnimationClock = interaction.value) }
                    if (initial.emoteAnimationClock != interaction.value) {
                        _events.emit(DeveloperSettingsEvent.RestartRequired)
                    }
                }

                is DeveloperSettingsInteraction.ChatSendProtocolChanged -> {
                    developerSettingsDataStore.update { it.copy(chatSendProtocol = interaction.protocol) }
                }

                is DeveloperSettingsInteraction.RestartRequired -> {
                    _events.emit(DeveloperSettingsEvent.RestartRequired)
                }

                is DeveloperSettingsInteraction.ResetOnboarding -> {
                    onboardingDataStore.update {
                        it.copy(
                            hasCompletedOnboarding = false,
                            onboardingPage = 0,
                        )
                    }
                    _events.emit(DeveloperSettingsEvent.RestartRequired)
                }

                is DeveloperSettingsInteraction.ResetTour -> {
                    onboardingDataStore.update {
                        it.copy(
                            featureTourVersion = 0,
                            featureTourStep = 0,
                            hasShownAddChannelHint = false,
                            hasShownToolbarHint = false,
                        )
                    }
                    _events.emit(DeveloperSettingsEvent.RestartRequired)
                }

                is DeveloperSettingsInteraction.RevokeToken -> {
                    val token = authDataStore.oAuthKey?.withoutOAuthPrefix ?: return@launch
                    val clientId = authDataStore.clientId
                    authApiClient.revokeToken(token, clientId)
                    _events.emit(DeveloperSettingsEvent.ImmediateRestart)
                }
            }
        }
    }
}
