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

    private val messageHighlightsTab = MutableStateFlow(HighlightsTabItem(HighlightsTab.Messages, listOf(AddItem)))
    private val userHighlightsTab = MutableStateFlow(HighlightsTabItem(HighlightsTab.Users, listOf(AddItem)))
    private val eventChannel = Channel<HighlightEvent>(Channel.CONFLATED)

    val events = eventChannel.receiveAsFlow()
    val highlightTabs = combine(messageHighlightsTab, userHighlightsTab) { messageHighlights, userHighlights ->
        listOf(messageHighlights, userHighlights)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), INITIAL_STATE)

    fun fetchHighlights() {
        val messageHighlights = highlightsRepository.messageHighlights.value.map { it.toItem() }
        val userHighlights = highlightsRepository.userHighlights.value.map { it.toItem() }

        messageHighlightsTab.update { it.copy(items = listOf(AddItem) + messageHighlights) }
        userHighlightsTab.update { it.copy(items = listOf(AddItem) + userHighlights) }
    }

    fun addHighlight(tab: HighlightsTab) = viewModelScope.launch {
        when (tab) {
            HighlightsTab.Messages -> {
                val entity = highlightsRepository.addMessageHighlight()
                messageHighlightsTab.update {
                    it.copy(items = it.items + entity.toItem())
                }
            }

            HighlightsTab.Users    -> {
                val entity = highlightsRepository.addUserHighlight()
                userHighlightsTab.update {
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

            else                    -> Unit
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

            else                    -> Unit
        }
    }

    fun updateHighlights(tabItems: List<HighlightsTabItem>) = viewModelScope.launch {
        tabItems.forEach { tab ->
            when (tab.tab) {
                HighlightsTab.Messages -> {
                    val (blankEntities, entities) = tab.items
                        .filterIsInstance<MessageHighlightItem>()
                        .map { it.toEntity() }
                        .partition { it.type == MessageHighlightEntityType.Custom && it.pattern.isBlank() }

                    highlightsRepository.updateMessageHighlights(entities)
                    blankEntities.forEach { highlightsRepository.removeMessageHighlight(it) }
                }

                HighlightsTab.Users    -> {
                    val (blankEntities, entities) = tab.items
                        .filterIsInstance<UserHighlightItem>()
                        .map { it.toEntity() }
                        .partition { it.username.isBlank() }

                    highlightsRepository.updateUserHighlights(entities)
                    blankEntities.forEach { highlightsRepository.removeUserHighlight(it) }
                }
            }
        }
    }

    companion object {
        private val INITIAL_STATE = listOf(
            HighlightsTabItem(HighlightsTab.Messages, listOf(AddItem)),
            HighlightsTabItem(HighlightsTab.Users, listOf(AddItem)),
        )
    }
}