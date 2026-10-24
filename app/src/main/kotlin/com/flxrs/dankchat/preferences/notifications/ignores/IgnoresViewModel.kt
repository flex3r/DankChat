package com.flxrs.dankchat.preferences.notifications.ignores

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntityType
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.extensions.replaceAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class IgnoresViewModel(
    private val ignoresRepository: IgnoresRepository,
    private val dispatchersProvider: DispatchersProvider,
) : ViewModel() {
    private val _currentTab = MutableStateFlow(IgnoresTab.Messages)
    private val eventChannel = Channel<IgnoreEvent>(Channel.BUFFERED)

    val messageIgnores = SnapshotStateList<MessageIgnoreItem>()
    val userIgnores = SnapshotStateList<UserIgnoreItem>()
    val twitchBlocks = SnapshotStateList<TwitchBlockItem>()

    val events = eventChannel.receiveAsFlow()
    val currentTab = _currentTab.asStateFlow()

    fun setCurrentTab(position: Int) {
        _currentTab.value = IgnoresTab.entries[position]
    }

    fun fetchIgnores() {
        val messageIgnoreItems = ignoresRepository.messageIgnores.value.map { it.toItem() }
        val userIgnoreItems = ignoresRepository.userIgnores.value.map { it.toItem() }
        val twitchBlockItems = ignoresRepository.twitchBlocks.value.map { it.toItem() }

        messageIgnores.replaceAll(messageIgnoreItems)
        userIgnores.replaceAll(userIgnoreItems)
        twitchBlocks.replaceAll(twitchBlockItems)
    }

    fun addIgnore() = viewModelScope.launch {
        val position: Int
        when (_currentTab.value) {
            IgnoresTab.Messages -> {
                val entity = ignoresRepository.addMessageIgnore()
                messageIgnores += entity.toItem()
                position = messageIgnores.lastIndex
            }

            IgnoresTab.Users -> {
                val entity = ignoresRepository.addUserIgnore()
                userIgnores += entity.toItem()
                position = userIgnores.lastIndex
            }

            IgnoresTab.Twitch -> {
                return@launch
            }
        }
        sendEvent(IgnoreEvent.ItemAdded(position, isLast = true))
    }

    fun addIgnoreItem(
        item: IgnoreItem,
        position: Int,
    ) = viewModelScope.launch {
        val isLast: Boolean
        when (item) {
            is MessageIgnoreItem -> {
                ignoresRepository.updateMessageIgnore(item.toEntity())
                messageIgnores.add(position, item)
                isLast = position == messageIgnores.lastIndex
            }

            is UserIgnoreItem -> {
                ignoresRepository.updateUserIgnore(item.toEntity())
                userIgnores.add(position, item)
                isLast = position == userIgnores.lastIndex
            }

            is TwitchBlockItem -> {
                runCatching {
                    ignoresRepository.addUserBlock(item.userId, item.username)
                    twitchBlocks.add(position, item)
                }.getOrElse {
                    eventChannel.trySend(IgnoreEvent.BlockError(item))
                    return@launch
                }
                isLast = position == twitchBlocks.lastIndex
            }
        }
        sendEvent(IgnoreEvent.ItemAdded(position, isLast))
    }

    fun removeIgnore(item: IgnoreItem) = viewModelScope.launch {
        val position: Int
        when (item) {
            is MessageIgnoreItem -> {
                position = messageIgnores.indexOfFirst { it.id == item.id }
                if (position < 0) {
                    return@launch
                }
                messageIgnores.removeAt(position)
                ignoresRepository.removeMessageIgnore(item.toEntity())
            }

            is UserIgnoreItem -> {
                position = userIgnores.indexOfFirst { it.id == item.id }
                if (position < 0) {
                    return@launch
                }
                userIgnores.removeAt(position)
                ignoresRepository.removeUserIgnore(item.toEntity())
            }

            is TwitchBlockItem -> {
                position = twitchBlocks.indexOfFirst { it.id == item.id }
                if (position < 0) {
                    return@launch
                }
                twitchBlocks.removeAt(position)
                runCatching {
                    ignoresRepository.removeUserBlock(item.userId, item.username)
                }.getOrElse {
                    twitchBlocks.add(position.coerceAtMost(twitchBlocks.size), item)
                    eventChannel.trySend(IgnoreEvent.UnblockError(item))
                    return@launch
                }
            }
        }
        sendEvent(IgnoreEvent.ItemRemoved(item, position))
    }

    fun updateIgnores(
        messageIgnoreItems: List<MessageIgnoreItem>,
        userIgnoreItems: List<UserIgnoreItem>,
    ) = viewModelScope.launch {
        filterMessageIgnores(messageIgnoreItems).let { (blankEntities, entities) ->
            ignoresRepository.updateMessageIgnores(entities)
            blankEntities.forEach { ignoresRepository.removeMessageIgnore(it) }
        }

        filterUserIgnores(userIgnoreItems).let { (blankEntities, entities) ->
            ignoresRepository.updateUserIgnores(entities)
            blankEntities.forEach { ignoresRepository.removeUserIgnore(it) }
        }
    }

    private fun filterMessageIgnores(items: List<MessageIgnoreItem>) = items
        .map { it.toEntity() }
        .partition { it.type == MessageIgnoreEntityType.Custom && it.pattern.isBlank() }

    private fun filterUserIgnores(items: List<UserIgnoreItem>) = items
        .map { it.toEntity() }
        .partition { it.username.isBlank() }

    private suspend fun sendEvent(event: IgnoreEvent) = withContext(dispatchersProvider.immediate) {
        eventChannel.send(event)
    }

    companion object {
        const val REGEX_INFO_URL = "https://wiki.chatterino.com/Regex/"
    }
}
