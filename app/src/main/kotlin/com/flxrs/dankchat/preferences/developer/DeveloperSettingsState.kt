package com.flxrs.dankchat.preferences.developer

sealed interface DeveloperSettingsEvent {
    data object RestartRequired : DeveloperSettingsEvent

    data object ImmediateRestart : DeveloperSettingsEvent
}

sealed interface DeveloperSettingsInteraction {
    data class DebugMode(
        val value: Boolean,
    ) : DeveloperSettingsInteraction

    data class RepeatedSending(
        val value: Boolean,
    ) : DeveloperSettingsInteraction

    data class BypassCommandHandling(
        val value: Boolean,
    ) : DeveloperSettingsInteraction

    data class CustomRecentMessagesHost(
        val host: String,
    ) : DeveloperSettingsInteraction

    data class EventSubEnabled(
        val value: Boolean,
    ) : DeveloperSettingsInteraction

    data class EventSubDebugOutput(
        val value: Boolean,
    ) : DeveloperSettingsInteraction

    data class EmoteAnimationClock(
        val value: Boolean,
    ) : DeveloperSettingsInteraction

    data class ChatSendProtocolChanged(
        val protocol: ChatSendProtocol,
    ) : DeveloperSettingsInteraction

    data object RestartRequired : DeveloperSettingsInteraction

    data object ResetOnboarding : DeveloperSettingsInteraction

    data object ResetTour : DeveloperSettingsInteraction

    data object RevokeToken : DeveloperSettingsInteraction
}
