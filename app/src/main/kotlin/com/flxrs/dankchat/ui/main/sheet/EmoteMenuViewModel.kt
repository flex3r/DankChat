package com.flxrs.dankchat.ui.main.sheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.data.repo.emote.EmoteUsageRepository
import com.flxrs.dankchat.data.repo.emote.Emotes
import com.flxrs.dankchat.data.twitch.emote.EmoteType
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.ui.chat.emotemenu.EmoteMenuTab
import com.flxrs.dankchat.ui.chat.emotemenu.EmoteMenuTabItem
import com.flxrs.dankchat.ui.chat.emotemenu.EmoteMenuUiState
import com.flxrs.dankchat.utils.extensions.flatMapLatestOrDefault
import com.flxrs.dankchat.utils.extensions.toEmoteItems
import com.flxrs.dankchat.utils.extensions.toEmoteItemsWithFront
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class EmoteMenuViewModel(
    dataRepository: DataRepository,
    chatChannelProvider: ChatChannelProvider,
    emoteUsageRepository: EmoteUsageRepository,
    chatSettingsDataStore: ChatSettingsDataStore,
    dispatchersProvider: DispatchersProvider,
) : ViewModel() {
    private val selectedTabIndex = MutableStateFlow(0)

    fun selectTab(index: Int) {
        selectedTabIndex.value = index
    }

    private val activeChannel = chatChannelProvider.activeChannel

    private val emotes =
        activeChannel
            .flatMapLatestOrDefault(Emotes()) { dataRepository.getEmotes(it) }
            .flowOn(dispatchersProvider.default)

    private val recentEmotes =
        emoteUsageRepository.getRecentUsages().distinctUntilChanged { old, new ->
            new.all { newEmote -> old.any { it.emoteId == newEmote.emoteId } }
        }

    private val emoteTabItems =
        combine(emotes, recentEmotes, activeChannel) { emotes, recentEmotes, channel ->
            withContext(dispatchersProvider.default) {
                val sortedEmotes = emotes.sorted
                val availableRecents =
                    recentEmotes.mapNotNull { usage ->
                        sortedEmotes
                            .firstOrNull { it.id == usage.emoteId }
                            ?.copy(emoteType = EmoteType.RecentUsageEmote)
                    }

                val groupedByType =
                    sortedEmotes.groupBy {
                        when (it.emoteType) {
                            is EmoteType.ChannelTwitchEmote,
                            is EmoteType.ChannelTwitchBitEmote,
                            is EmoteType.ChannelTwitchFollowerEmote,
                            -> EmoteMenuTab.SUBS

                            is EmoteType.ChannelFFZEmote,
                            is EmoteType.ChannelBTTVEmote,
                            is EmoteType.ChannelSevenTVEmote,
                            -> EmoteMenuTab.CHANNEL

                            else -> EmoteMenuTab.GLOBAL
                        }
                    }
                listOf(
                    async { EmoteMenuTabItem(EmoteMenuTab.RECENT, availableRecents.toEmoteItems()) },
                    async { EmoteMenuTabItem(EmoteMenuTab.SUBS, groupedByType[EmoteMenuTab.SUBS].toEmoteItemsWithFront(channel)) },
                    async { EmoteMenuTabItem(EmoteMenuTab.CHANNEL, groupedByType[EmoteMenuTab.CHANNEL].orEmpty().toEmoteItems()) },
                    async { EmoteMenuTabItem(EmoteMenuTab.GLOBAL, groupedByType[EmoteMenuTab.GLOBAL].orEmpty().toEmoteItems()) },
                ).awaitAll().toImmutableList()
            }
        }

    val uiState: StateFlow<EmoteMenuUiState> =
        combine(emoteTabItems, selectedTabIndex, chatSettingsDataStore.animateGifs) { tabItems, tabIndex, animateGifs ->
            EmoteMenuUiState(tabItems = tabItems, selectedTabIndex = tabIndex, animateEmotes = animateGifs)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            EmoteMenuUiState(
                tabItems = EmoteMenuTab.entries.map { EmoteMenuTabItem(it, emptyList()) }.toImmutableList(),
                selectedTabIndex = 0,
                animateEmotes = chatSettingsDataStore.current().animateGifs,
            ),
        )
}
