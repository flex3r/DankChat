package com.flxrs.dankchat.preferences.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class NotificationsSettingsViewModel(
    private val notificationsSettingsDataStore: NotificationsSettingsDataStore,
) : ViewModel() {

    val settings = notificationsSettingsDataStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds),
            initialValue = notificationsSettingsDataStore.current(),
        )

    fun onInteraction(interaction: NotificationsSettingsInteraction) = viewModelScope.launch {
        runCatching {
            when (interaction) {
                is NotificationsSettingsInteraction.Notifications        -> notificationsSettingsDataStore.update { it.copy(showNotifications = interaction.value) }
                is NotificationsSettingsInteraction.WhisperNotifications -> notificationsSettingsDataStore.update { it.copy(showWhisperNotifications = interaction.value) }
                is NotificationsSettingsInteraction.Mention              -> notificationsSettingsDataStore.update { it.copy(mentionFormat = interaction.value) }
            }
        }
    }
}

sealed interface NotificationsSettingsInteraction {
    data class Notifications(val value: Boolean) : NotificationsSettingsInteraction
    data class WhisperNotifications(val value: Boolean) : NotificationsSettingsInteraction
    data class Mention(val value: MentionFormat) : NotificationsSettingsInteraction
}
