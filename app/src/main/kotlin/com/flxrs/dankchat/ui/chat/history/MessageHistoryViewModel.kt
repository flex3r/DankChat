package com.flxrs.dankchat.ui.chat.history

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.repo.chat.ChatMessageRepository
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessage
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.ui.chat.ChatDisplaySettings
import com.flxrs.dankchat.ui.chat.ChatMessageMapper
import com.flxrs.dankchat.ui.chat.ChatMessageMappingCache
import com.flxrs.dankchat.ui.chat.ChatMessageUiState
import com.flxrs.dankchat.ui.chat.CheckeredMessageTracker
import com.flxrs.dankchat.ui.chat.search.ChatItemFilter
import com.flxrs.dankchat.ui.chat.search.ChatSearchFilter
import com.flxrs.dankchat.ui.chat.search.ChatSearchFilterParser
import com.flxrs.dankchat.ui.chat.search.SearchFilterSuggestions
import com.flxrs.dankchat.ui.chat.suggestion.Suggestion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class MessageHistoryViewModel(
    @InjectedParam initialChannel: HistoryChannel,
    chatMessageRepository: ChatMessageRepository,
    usersRepository: UsersRepository,
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
    private val _selectedChannel = MutableStateFlow(initialChannel)
    val selectedChannel: StateFlow<HistoryChannel> = _selectedChannel

    val availableChannels: StateFlow<ImmutableList<HistoryChannel>> =
        combine(
            chatMessageRepository.channels,
            preferenceStore.getChannelsWithRenamesFlow(),
        ) { channels, preferredOrder ->
            val orderIndex = preferredOrder.withIndex().associate { (idx, entry) -> entry.channel to idx }
            val sortedChannels = channels.sortedBy { orderIndex[it] ?: Int.MAX_VALUE }
            buildList {
                add(HistoryChannel.Global)
                sortedChannels.forEach { add(HistoryChannel.Channel(it)) }
            }.toImmutableList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf(HistoryChannel.Global))

    val isGlobal: StateFlow<Boolean> =
        _selectedChannel
            .map { it is HistoryChannel.Global }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialChannel is HistoryChannel.Global)

    fun selectChannel(channel: HistoryChannel) {
        _selectedChannel.value = channel
    }

    val searchFieldState = TextFieldState()
    private var appliedOpenId: Long? = null

    private val searchQuery =
        snapshotFlow { searchFieldState.text.toString() }
            .distinctUntilChanged()

    private val filters: Flow<List<ChatSearchFilter>> =
        merge(
            searchQuery.take(1),
            searchQuery.drop(1).debounce(300),
        ).map { ChatSearchFilterParser.parse(it) }
            .distinctUntilChanged()

    // Shared so the global chat merge runs once per message instead of once per collector
    private val messagesFlow: Flow<List<ChatItem>> =
        _selectedChannel
            .flatMapLatest { channel ->
                when (channel) {
                    is HistoryChannel.Global -> chatMessageRepository.getAllChat()
                    is HistoryChannel.Channel -> chatMessageRepository.getChat(channel.name)
                }
            }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val historyUiStates: Flow<ImmutableList<ChatMessageUiState>> =
        combine(
            messagesFlow,
            filters,
            appearanceSettingsDataStore.settings,
            chatSettingsDataStore.settings,
        ) { messages, activeFilters, appearanceSettings, chatSettings ->
            mappingCache
                .mapWithHighlightLayout(
                    items = messages.filter { it.message !is SystemMessage && ChatItemFilter.matches(it, activeFilters) },
                    chatSettings = chatSettings,
                    showLineSeparator = appearanceSettings.lineSeparator,
                    isAlternateBackground = { appearanceSettings.checkeredMessages && checkeredTracker.isAlternate(it.message.id) },
                    // Sized to every message, so changing the filter maps only messages that were never shown
                    cacheSize = messages.size,
                ) { item, altBg ->
                    chatMessageMapper.mapToUiState(
                        item = item,
                        chatSettings = chatSettings,
                        preferenceStore = preferenceStore,
                        isAlternateBackground = altBg,
                    )
                }.toImmutableList()
        }.flowOn(dispatchersProvider.default)

    private val users: StateFlow<ImmutableSet<DisplayName>> =
        _selectedChannel
            .flatMapLatest { channel ->
                when (channel) {
                    is HistoryChannel.Channel -> usersRepository.getUsersFlow(channel.name).map { it.toSet() }

                    is HistoryChannel.Global -> chatMessageRepository.channels.flatMapLatest { channels ->
                        when {
                            channels.isEmpty() -> flowOf(emptySet())

                            else -> combine(channels.map { usersRepository.getUsersFlow(it) }) { arrays ->
                                arrays.flatMap { it }.toSet()
                            }
                        }
                    }
                }
            }.map { it.toImmutableSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentSetOf())

    private val badgeNames: StateFlow<ImmutableSet<String>> =
        messagesFlow
            .map { items ->
                items
                    .asSequence()
                    .map { it.message }
                    .filterIsInstance<PrivMessage>()
                    .flatMap { it.badges }
                    .mapNotNull { it.badgeTag?.substringBefore('/') }
                    .toImmutableSet()
            }.flowOn(dispatchersProvider.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentSetOf())

    val filterSuggestions: StateFlow<ImmutableList<Suggestion>> =
        combine(
            searchQuery,
            users,
            badgeNames,
        ) { query, userSet, badges ->
            SearchFilterSuggestions.filter(query, users = userSet, badgeNames = badges).toImmutableList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    fun setInitialQuery(
        openId: Long,
        query: String,
    ) {
        if (openId == appliedOpenId) {
            return
        }
        appliedOpenId = openId

        if (query.isNotEmpty()) {
            val normalizedQuery = if (query.endsWith(' ')) query else "$query "
            searchFieldState.edit {
                replace(0, length, normalizedQuery)
                placeCursorAtEnd()
            }
        } else {
            searchFieldState.clearText()
        }
    }

    fun insertText(text: String) {
        searchFieldState.edit {
            append(text)
            placeCursorAtEnd()
        }
    }

    fun applySuggestion(suggestion: Suggestion) {
        val currentText = searchFieldState.text.toString()
        val lastSpaceIndex = currentText.trimEnd().lastIndexOf(' ')
        val prefix =
            when {
                lastSpaceIndex >= 0 -> currentText.substring(0, lastSpaceIndex + 1)
                else -> ""
            }
        val keyword = suggestion.toString()
        val suffix =
            when {
                keyword.endsWith(':') -> ""
                else -> " "
            }
        val newText = prefix + keyword + suffix
        searchFieldState.edit {
            replace(0, length, newText)
            selection = TextRange(newText.length)
        }
    }
}
