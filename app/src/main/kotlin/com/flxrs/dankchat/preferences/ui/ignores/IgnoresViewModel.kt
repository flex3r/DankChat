package com.flxrs.dankchat.preferences.ui.ignores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntityType
import com.flxrs.dankchat.data.repo.IgnoresRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class IgnoresViewModel @Inject constructor(
    private val ignoresRepository: IgnoresRepository
) : ViewModel() {

    private val _currentTab = MutableStateFlow(IgnoresTab.Messages)
    private val messageIgnoresTab = MutableStateFlow(IgnoresTabItem(IgnoresTab.Messages, listOf(AddItem)))
    private val userIgnoresTab = MutableStateFlow(IgnoresTabItem(IgnoresTab.Users, listOf(AddItem)))
    private val twitchBlocksTab = MutableStateFlow(IgnoresTabItem(IgnoresTab.Twitch, emptyList()))
    private val eventChannel = Channel<IgnoreEvent>(Channel.CONFLATED)

    val events = eventChannel.receiveAsFlow()
    val ignoreTabs = combine(messageIgnoresTab, userIgnoresTab, twitchBlocksTab) { messageIgnoresTab, userIgnoresTab, twitchBlocksTab ->
        listOf(messageIgnoresTab, userIgnoresTab, twitchBlocksTab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), INITIAL_STATE)
    val currentTab = _currentTab.asStateFlow()

    fun setCurrentTab(position: Int) {
        _currentTab.value = IgnoresTab.entries[position]
    }

    fun fetchIgnores() {
        val messageIgnores = ignoresRepository.messageIgnores.value.map { it.toItem() }
        val userIgnores = ignoresRepository.userIgnores.value.map { it.toItem() }
        val twitchBlocks = ignoresRepository.twitchBlocks.value.map { it.toItem() }

        messageIgnoresTab.update { it.copy(items = listOf(AddItem) + messageIgnores) }
        userIgnoresTab.update { it.copy(items = listOf(AddItem) + userIgnores) }
        twitchBlocksTab.update { it.copy(items = twitchBlocks) }
    }

    fun addIgnore() = viewModelScope.launch {
        when (_currentTab.value) {
            IgnoresTab.Messages -> {
                val entity = ignoresRepository.addMessageIgnore()
                messageIgnoresTab.update {
                    it.copy(items = it.items + entity.toItem())
                }
            }

            IgnoresTab.Users    -> {
                val entity = ignoresRepository.addUserIgnore()
                userIgnoresTab.update {
                    it.copy(items = it.items + entity.toItem())
                }
            }

            IgnoresTab.Twitch   -> Unit
        }
    }

    fun addIgnoreItem(item: IgnoreItem, position: Int) = viewModelScope.launch {
        when (item) {
            is MessageIgnoreItem -> {
                ignoresRepository.updateMessageIgnore(item.toEntity())
                messageIgnoresTab.update {
                    val mutableItems = it.items.toMutableList()
                    mutableItems.add(position, item)
                    it.copy(items = mutableItems)
                }
            }

            is UserIgnoreItem    -> {
                ignoresRepository.updateUserIgnore(item.toEntity())
                userIgnoresTab.update {
                    val mutableItems = it.items.toMutableList()
                    mutableItems.add(position, item)
                    it.copy(items = mutableItems)
                }
            }

            is TwitchBlockItem   -> {
                runCatching {
                    ignoresRepository.addUserBlock(item.userId, item.username)
                    twitchBlocksTab.update {
                        val mutableItems = it.items.toMutableList()
                        mutableItems.add(position, item)
                        it.copy(items = mutableItems)
                    }
                }.getOrElse {
                    eventChannel.trySend(IgnoreEvent.BlockError(item))
                }
            }

            else                 -> Unit
        }
    }

    fun removeIgnore(item: IgnoreItem) = viewModelScope.launch {
        when (item) {
            is MessageIgnoreItem -> {
                val position = messageIgnoresTab.value.items.indexOf(item)
                ignoresRepository.removeMessageIgnore(item.toEntity())
                messageIgnoresTab.update { it.copy(items = it.items - item) }
                eventChannel.trySend(IgnoreEvent.ItemRemoved(item, position))
            }

            is UserIgnoreItem    -> {
                val position = userIgnoresTab.value.items.indexOf(item)
                ignoresRepository.removeUserIgnore(item.toEntity())
                userIgnoresTab.update { it.copy(items = it.items - item) }
                eventChannel.trySend(IgnoreEvent.ItemRemoved(item, position))
            }

            is TwitchBlockItem   -> {
                runCatching {
                    val position = twitchBlocksTab.value.items.indexOf(item)
                    ignoresRepository.removeUserBlock(item.userId, item.username)
                    twitchBlocksTab.update { it.copy(items = it.items - item) }
                    eventChannel.trySend(IgnoreEvent.ItemRemoved(item, position))
                }.getOrElse {
                    eventChannel.trySend(IgnoreEvent.UnblockError(item))
                }
            }

            else                 -> Unit
        }
    }

    fun updateIgnores(tabItems: List<IgnoresTabItem>) = viewModelScope.launch {
        tabItems.forEach { tab ->
            when (tab.tab) {
                IgnoresTab.Messages -> {
                    val (blankEntities, entities) = tab.items
                        .filterIsInstance<MessageIgnoreItem>()
                        .map { it.toEntity() }
                        .partition { it.type == MessageIgnoreEntityType.Custom && it.pattern.isBlank() }

                    ignoresRepository.updateMessageIgnores(entities)
                    blankEntities.forEach { ignoresRepository.removeMessageIgnore(it) }
                }

                IgnoresTab.Users    -> {
                    val (blankEntities, entities) = tab.items
                        .filterIsInstance<UserIgnoreItem>()
                        .map { it.toEntity() }
                        .partition { it.username.isBlank() }

                    ignoresRepository.updateUserIgnores(entities)
                    blankEntities.forEach { ignoresRepository.removeUserIgnore(it) }
                }

                else                -> Unit
            }
        }
    }

    companion object {
        private val INITIAL_STATE = listOf(
            IgnoresTabItem(IgnoresTab.Messages, listOf(AddItem)),
            IgnoresTabItem(IgnoresTab.Users, listOf(AddItem)),
            IgnoresTabItem(IgnoresTab.Twitch, emptyList()),
        )
    }
}
