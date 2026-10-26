package com.flxrs.dankchat.ui.chat.replies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.RepliesRepository
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.ui.chat.ChatDisplaySettings
import com.flxrs.dankchat.ui.chat.ChatMessageMapper
import com.flxrs.dankchat.ui.chat.ChatMessageMappingCache
import com.flxrs.dankchat.ui.chat.CheckeredMessageTracker
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class RepliesViewModel(
    @InjectedParam private val rootMessageId: String,
    repliesRepository: RepliesRepository,
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

    private val checkeredTracker = CheckeredMessageTracker()
    private val mappingCache = ChatMessageMappingCache()

    val state =
        repliesRepository
            .getThreadItemsFlow(rootMessageId)
            .map {
                when {
                    it.isEmpty() -> RepliesState.NotFound
                    else -> RepliesState.Found(it.toImmutableList())
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RepliesState.Found(persistentListOf()))

    val uiState: StateFlow<RepliesUiState> =
        combine(
            state,
            appearanceSettingsDataStore.settings,
            chatSettingsDataStore.settings,
        ) { repliesState, appearanceSettings, chatSettings ->
            when (repliesState) {
                is RepliesState.NotFound -> {
                    RepliesUiState.NotFound
                }

                is RepliesState.Found -> {
                    val uiMessages = mappingCache
                        .mapWithHighlightLayout(
                            items = repliesState.items,
                            chatSettings = chatSettings,
                            showLineSeparator = appearanceSettings.lineSeparator,
                            isAlternateBackground = { checkeredTracker.isAlternate(it.message.id) && appearanceSettings.checkeredMessages },
                        ) { item, altBg ->
                            chatMessageMapper.mapToUiState(
                                item = item,
                                chatSettings = chatSettings,
                                preferenceStore = preferenceStore,
                                isAlternateBackground = altBg,
                            )
                        }.toImmutableList()
                    RepliesUiState.Found(uiMessages)
                }
            }
        }.flowOn(dispatchersProvider.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RepliesUiState.Found(persistentListOf()))
}
