package com.flxrs.dankchat.preferences.ui.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntityType
import com.flxrs.dankchat.data.repo.HighlightsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class HighlightsViewModel @Inject constructor(
    private val highlightsRepository: HighlightsRepository
) : ViewModel() {

    private val _currentTab = MutableStateFlow(HighlightsTab.Messages)
    private val messageHighlightsTab = MutableStateFlow(HighlightsTabItem(HighlightsTab.Messages, listOf(AddItem)))
    private val userHighlightsTab = MutableStateFlow(HighlightsTabItem(HighlightsTab.Users, listOf(AddItem)))
    private val blacklistedUsersTab = MutableStateFlow(HighlightsTabItem(HighlightsTab.BlacklistedUsers, listOf(AddItem)))
    private val eventChannel = Channel<HighlightEvent>(Channel.CONFLATED)

    val events = eventChannel.receiveAsFlow()
    val highlightTabs = combine(messageHighlightsTab, userHighlightsTab, blacklistedUsersTab) { messageHighlights, userHighlights, blacklistedUsersTab ->
        listOf(messageHighlights, userHighlights, blacklistedUsersTab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), INITIAL_STATE)
    val currentTab = _currentTab.asStateFlow()

    fun setCurrentTab(position: Int) {
        _currentTab.value = HighlightsTab.values()[position]
    }

    fun fetchHighlights() {
        val messageHighlights = highlightsRepository.messageHighlights.value.map { it.toItem() }
        val userHighlights = highlightsRepository.userHighlights.value.map { it.toItem() }
        val blacklistedUsers = highlightsRepository.blacklistedUsers.value.map { it.toItem() }

        messageHighlightsTab.update { it.copy(items = listOf(AddItem) + messageHighlights) }
        userHighlightsTab.update { it.copy(items = listOf(AddItem) + userHighlights) }
        blacklistedUsersTab.update { it.copy(items = listOf(AddItem) + blacklistedUsers) }
    }

    fun addHighlight() = viewModelScope.launch {
        when (_currentTab.value) {
            HighlightsTab.Messages         -> {
                val entity = highlightsRepository.addMessageHighlight()
                messageHighlightsTab.update {
                    it.copy(items = it.items + entity.toItem())
                }
            }

            HighlightsTab.Users            -> {
                val entity = highlightsRepository.addUserHighlight()
                userHighlightsTab.update {
                    it.copy(items = it.items + entity.toItem())
                }
            }

            HighlightsTab.BlacklistedUsers -> {
                val entity = highlightsRepository.addBlacklistedUser()
                blacklistedUsersTab.update {
                    it.copy(items = it.items + entity.toItem())
                }
            }
        }
    }

    fun addHighlightItem(item: HighlightItem, position: Int) = viewModelScope.launch {
        when (item) {
            is MessageHighlightItem -> {
                highlightsRepository.updateMessageHighlight(item.toEntity())
                messageHighlightsTab.update {
                    val mutableItems = it.items.toMutableList()
                    mutableItems.add(position, item)
                    it.copy(items = mutableItems)
                }
            }

            is UserHighlightItem    -> {
                highlightsRepository.updateUserHighlight(item.toEntity())
                userHighlightsTab.update {
                    val mutableItems = it.items.toMutableList()
                    mutableItems.add(position, item)
                    it.copy(items = mutableItems)
                }
            }

            is BlacklistedUserItem  -> {
                highlightsRepository.updateBlacklistedUser(item.toEntity())
                blacklistedUsersTab.update {
                    val mutableItems = it.items.toMutableList()
                    mutableItems.add(position, item)
                    it.copy(items = mutableItems)
                }
            }

            is AddItem              -> Unit
        }
    }

    fun removeHighlight(item: HighlightItem) = viewModelScope.launch {
        when (item) {
            is MessageHighlightItem -> {
                val position = messageHighlightsTab.value.items.indexOf(item)
                highlightsRepository.removeMessageHighlight(item.toEntity())
                messageHighlightsTab.update { it.copy(items = it.items - item) }
                eventChannel.trySend(HighlightEvent.ItemRemoved(item, position))
            }

            is UserHighlightItem    -> {
                val position = userHighlightsTab.value.items.indexOf(item)
                highlightsRepository.removeUserHighlight(item.toEntity())
                userHighlightsTab.update { it.copy(items = it.items - item) }
                eventChannel.trySend(HighlightEvent.ItemRemoved(item, position))
            }

            is BlacklistedUserItem  -> {
                val position = blacklistedUsersTab.value.items.indexOf(item)
                highlightsRepository.removeBlacklistedUser(item.toEntity())
                blacklistedUsersTab.update { it.copy(items = it.items - item) }
                eventChannel.trySend(HighlightEvent.ItemRemoved(item, position))
            }

            is AddItem              -> Unit
        }
    }

    fun updateHighlights(tabItems: List<HighlightsTabItem>) = viewModelScope.launch {
        tabItems.forEach { tab ->
            when (tab.tab) {
                HighlightsTab.Messages         -> {
                    val (blankEntities, entities) = tab.items
                        .filterIsInstance<MessageHighlightItem>()
                        .map { it.toEntity() }
                        .partition { it.type == MessageHighlightEntityType.Custom && it.pattern.isBlank() }

                    highlightsRepository.updateMessageHighlights(entities)
                    blankEntities.forEach { highlightsRepository.removeMessageHighlight(it) }
                }

                HighlightsTab.Users            -> {
                    val (blankEntities, entities) = tab.items
                        .filterIsInstance<UserHighlightItem>()
                        .map { it.toEntity() }
                        .partition { it.username.isBlank() }

                    highlightsRepository.updateUserHighlights(entities)
                    blankEntities.forEach { highlightsRepository.removeUserHighlight(it) }
                }

                HighlightsTab.BlacklistedUsers -> {
                    val (blankEntities, entities) = tab.items
                        .filterIsInstance<BlacklistedUserItem>()
                        .map { it.toEntity() }
                        .partition { it.username.isBlank() }

                    highlightsRepository.updateBlacklistedUser(entities)
                    blankEntities.forEach { highlightsRepository.removeBlacklistedUser(it) }
                }
            }
        }
    }

    companion object {
        private val INITIAL_STATE = listOf(
            HighlightsTabItem(HighlightsTab.Messages, listOf(AddItem)),
            HighlightsTabItem(HighlightsTab.Users, listOf(AddItem)),
            HighlightsTabItem(HighlightsTab.BlacklistedUsers, listOf(AddItem)),
        )
    }
}