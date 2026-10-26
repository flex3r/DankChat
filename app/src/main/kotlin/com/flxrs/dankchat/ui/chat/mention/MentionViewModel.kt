package com.flxrs.dankchat.ui.chat.mention

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.repo.chat.ChatNotificationRepository
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.ui.chat.ChatDisplaySettings
import com.flxrs.dankchat.ui.chat.ChatMessageMapper
import com.flxrs.dankchat.ui.chat.ChatMessageMappingCache
import com.flxrs.dankchat.ui.chat.ChatMessageUiState
import com.flxrs.dankchat.ui.chat.CheckeredMessageTracker
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class MentionViewModel(
    private val chatNotificationRepository: ChatNotificationRepository,
    chatMessageMapper: ChatMessageMapper,
    preferenceStore: DankChatPreferenceStore,
    appearanceSettingsDataStore: AppearanceSettingsDataStore,
    chatSettingsDataStore: ChatSettingsDataStore,
    dispatchersProvider: DispatchersProvider,
) : ViewModel() {
    val chatDisplaySettings: StateFlow<ChatDisplaySettings> =
        combine(
            appearanceSettingsDataStore.settings,
            chatSettingsDataStore.settings,
        ) { appearance, chat ->
            ChatDisplaySettings(
                fontSize = appearance.fontSize.toFloat(),
                animateGifs = chat.animateGifs,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatDisplaySettings())

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab

    val whisperMentionCount: StateFlow<Int> =
        chatNotificationRepository.channelMentionCount
            .map { it[WhisperMessage.WHISPER_CHANNEL] ?: 0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setCurrentTab(index: Int) {
        _currentTab.value = index
    }

    fun clearWhisperMentionCount() {
        chatNotificationRepository.clearMentionCount(WhisperMessage.WHISPER_CHANNEL)
    }

    fun markMentionsRead() {
        chatNotificationRepository.markMentionsRead()
    }

    fun markWhispersRead() {
        chatNotificationRepository.markWhispersRead()
    }

    val mentions: StateFlow<ImmutableList<ChatItem>> =
        chatNotificationRepository.mentions
            .map { it.toImmutableList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000), persistentListOf())
    val whispers: StateFlow<ImmutableList<ChatItem>> =
        chatNotificationRepository.whispers
            .map { it.toImmutableList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000), persistentListOf())

    private val mentionCheckered = CheckeredMessageTracker()
    private val whisperCheckered = CheckeredMessageTracker()
    private val mentionMappingCache = ChatMessageMappingCache()
    private val whisperMappingCache = ChatMessageMappingCache()

    init {
        combine(whispers, currentTab) { _, tab -> tab }
            .filter { it == 1 }
            .onEach { clearWhisperMentionCount() }
            .launchIn(viewModelScope)
    }

    val mentionsUiStates: Flow<ImmutableList<ChatMessageUiState>> =
        combine(
            mentions,
            appearanceSettingsDataStore.settings,
            chatSettingsDataStore.settings,
        ) { messages, appearanceSettings, chatSettings ->
            mentionMappingCache
                .mapWithHighlightLayout(
                    items = messages,
                    chatSettings = chatSettings,
                    showLineSeparator = appearanceSettings.lineSeparator,
                    isAlternateBackground = { mentionCheckered.isAlternate(it.message.id) && appearanceSettings.checkeredMessages },
                ) { item, altBg ->
                    chatMessageMapper.mapToUiState(
                        item = item,
                        chatSettings = chatSettings,
                        preferenceStore = preferenceStore,
                        isAlternateBackground = altBg,
                    )
                }.toImmutableList()
        }.flowOn(dispatchersProvider.default)

    val whispersUiStates: Flow<ImmutableList<ChatMessageUiState>> =
        combine(
            whispers,
            appearanceSettingsDataStore.settings,
            chatSettingsDataStore.settings,
        ) { messages, appearanceSettings, chatSettings ->
            whisperMappingCache
                .mapWithHighlightLayout(
                    items = messages,
                    chatSettings = chatSettings,
                    showLineSeparator = appearanceSettings.lineSeparator,
                    isAlternateBackground = { whisperCheckered.isAlternate(it.message.id) && appearanceSettings.checkeredMessages },
                ) { item, altBg ->
                    chatMessageMapper.mapToUiState(
                        item = item,
                        chatSettings = chatSettings,
                        preferenceStore = preferenceStore,
                        isAlternateBackground = altBg,
                    )
                }.toImmutableList()
        }.flowOn(dispatchersProvider.default)
}
