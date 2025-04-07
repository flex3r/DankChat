package com.flxrs.dankchat.preferences.notifications.highlights

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntityType
import com.flxrs.dankchat.data.repo.HighlightsRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.notifications.NotificationsSettingsDataStore
import com.flxrs.dankchat.utils.extensions.replaceAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class HighlightsViewModel(
    private val highlightsRepository: HighlightsRepository,
    private val preferenceStore: DankChatPreferenceStore,
    private val notificationsSettingsDataStore: NotificationsSettingsDataStore,
) : ViewModel() {

    private val _currentTab = MutableStateFlow(HighlightsTab.Messages)
    private val eventChannel = Channel<HighlightEvent>(Channel.BUFFERED)

    val messageHighlights = SnapshotStateList<MessageHighlightItem>()
    val userHighlights = SnapshotStateList<UserHighlightItem>()
    val blacklistedUsers = SnapshotStateList<BlacklistedUserItem>()

    val events = eventChannel.receiveAsFlow()
    val currentTab = _currentTab.asStateFlow()

    fun setCurrentTab(position: Int) {
        _currentTab.value = HighlightsTab.entries[position]
    }

    fun fetchHighlights() = viewModelScope.launch {
        val loggedIn = preferenceStore.isLoggedIn
        val notificationsEnabled = notificationsSettingsDataStore.settings.first().showNotifications
        val messageHighlightItems = highlightsRepository.messageHighlights.value.map { it.toItem(loggedIn, notificationsEnabled) }
        val userHighlightItems = highlightsRepository.userHighlights.value.map { it.toItem(notificationsEnabled) }
        val blacklistedUserItems = highlightsRepository.blacklistedUsers.value.map { it.toItem() }

        messageHighlights.replaceAll(messageHighlightItems)
        userHighlights.replaceAll(userHighlightItems)
        blacklistedUsers.replaceAll(blacklistedUserItems)
    }

    fun addHighlight() = viewModelScope.launch {
        val loggedIn = preferenceStore.isLoggedIn
        val notificationsEnabled = notificationsSettingsDataStore.settings.first().showNotifications
        val position: Int
        when (_currentTab.value) {
            HighlightsTab.Messages         -> {
                val entity = highlightsRepository.addMessageHighlight()
                messageHighlights += entity.toItem(loggedIn, notificationsEnabled)
                position = messageHighlights.lastIndex
            }

            HighlightsTab.Users            -> {
                val entity = highlightsRepository.addUserHighlight()
                userHighlights +=  entity.toItem(notificationsEnabled)
                position = userHighlights.lastIndex
            }

            HighlightsTab.BlacklistedUsers -> {
                val entity = highlightsRepository.addBlacklistedUser()
                blacklistedUsers += entity.toItem()
                position = blacklistedUsers.lastIndex
            }
        }
        sendEvent(HighlightEvent.ItemAdded(position, isLast = true))
    }

    fun addHighlightItem(item: HighlightItem, position: Int) = viewModelScope.launch {
        val isLast: Boolean
        when (item) {
            is MessageHighlightItem -> {
                highlightsRepository.updateMessageHighlight(item.toEntity())
                messageHighlights.add(position, item)
                isLast = position == messageHighlights.lastIndex
            }

            is UserHighlightItem    -> {
                highlightsRepository.updateUserHighlight(item.toEntity())
                userHighlights.add(position, item)
                isLast = position == userHighlights.lastIndex
            }

            is BlacklistedUserItem  -> {
                highlightsRepository.updateBlacklistedUser(item.toEntity())
                blacklistedUsers.add(position, item)
                isLast = position == blacklistedUsers.lastIndex
            }
        }
        sendEvent(HighlightEvent.ItemAdded(position, isLast))
    }

    fun removeHighlight(item: HighlightItem) = viewModelScope.launch {
        val position: Int
        when (item) {
            is MessageHighlightItem -> {
                position = messageHighlights.indexOfFirst { it.id == item.id }
                highlightsRepository.removeMessageHighlight(item.toEntity())
                messageHighlights.removeAt(position)
            }

            is UserHighlightItem    -> {
                position = userHighlights.indexOfFirst { it.id == item.id }
                highlightsRepository.removeUserHighlight(item.toEntity())
                userHighlights.removeAt(position)
            }

            is BlacklistedUserItem  -> {
                position = blacklistedUsers.indexOfFirst { it.id == item.id }
                highlightsRepository.removeBlacklistedUser(item.toEntity())
                blacklistedUsers.removeAt(position)
            }
        }
        sendEvent(HighlightEvent.ItemRemoved(item, position))
    }

    fun updateHighlights(
        messageHighlightItems: List<MessageHighlightItem>,
        userHighlightItems: List<UserHighlightItem>,
        blacklistedUserHighlightItems: List<BlacklistedUserItem>,
    ) = viewModelScope.launch {
        filterMessageHighlights(messageHighlightItems).let { (blankEntities, entities) ->
            highlightsRepository.updateMessageHighlights(entities)
            blankEntities.forEach { highlightsRepository.removeMessageHighlight(it) }
        }

        filterUserHighlights(userHighlightItems).let { (blankEntities, entities) ->
            highlightsRepository.updateUserHighlights(entities)
            blankEntities.forEach { highlightsRepository.removeUserHighlight(it) }
        }

        filterBlacklistedUsers(blacklistedUserHighlightItems).let { (blankEntities, entities) ->
            highlightsRepository.updateBlacklistedUser(entities)
            blankEntities.forEach { highlightsRepository.removeBlacklistedUser(it) }
        }
    }

    private fun filterMessageHighlights(items: List<MessageHighlightItem>) = items
        .map { it.toEntity() }
        .partition { it.type == MessageHighlightEntityType.Custom && it.pattern.isBlank() }

    private fun filterUserHighlights(items: List<UserHighlightItem>) = items
        .map { it.toEntity() }
        .partition { it.username.isBlank() }

    private fun filterBlacklistedUsers(items: List<BlacklistedUserItem>) = items
        .map { it.toEntity() }
        .partition { it.username.isBlank() }

    private suspend fun sendEvent(event: HighlightEvent) = withContext(Dispatchers.Main.immediate) {
        eventChannel.send(event)
    }

    companion object {
        const val REGEX_INFO_URL = "https://wiki.chatterino.com/Regex/"
    }
}
