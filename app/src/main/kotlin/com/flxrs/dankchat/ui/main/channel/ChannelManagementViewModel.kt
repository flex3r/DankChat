package com.flxrs.dankchat.ui.main.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.chat.ChannelSelectionDataStore
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.chat.ChatConnector
import com.flxrs.dankchat.data.repo.chat.ChatNotificationRepository
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.domain.ChannelDataCoordinator
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.model.ChannelWithRename
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class ChannelManagementViewModel(
    private val preferenceStore: DankChatPreferenceStore,
    private val channelDataCoordinator: ChannelDataCoordinator,
    private val chatRepository: ChatRepository,
    private val chatChannelProvider: ChatChannelProvider,
    private val chatConnector: ChatConnector,
    private val chatNotificationRepository: ChatNotificationRepository,
    private val ignoresRepository: IgnoresRepository,
    private val channelRepository: ChannelRepository,
    channelSelectionDataStore: ChannelSelectionDataStore,
) : ViewModel() {
    val channels: StateFlow<ImmutableList<ChannelWithRename>> =
        preferenceStore
            .getChannelsWithRenamesFlow()
            .map { it.toImmutableList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    init {
        // Restore persisted channel selection, falling back to first channel
        viewModelScope.launch {
            if (chatChannelProvider.activeChannel.value == null) {
                val channels = preferenceStore.channels
                val persisted = channelSelectionDataStore.current().activeChannel
                val restoredChannel = channels.firstOrNull { it.value == persisted } ?: channels.firstOrNull()
                if (restoredChannel != null) {
                    chatChannelProvider.setActiveChannel(restoredChannel)
                }
            }
        }

        // Auto-load data when channels added and join if necessary
        viewModelScope.launch {
            var previousChannels = emptySet<UserName>()
            channels.collect { channelList ->
                val currentChannels = channelList.map { it.channel }.toSet()
                val newChannels = currentChannels - previousChannels

                newChannels.forEach { channel ->
                    chatRepository.joinChannel(channel)
                    channelDataCoordinator.loadChannelData(channel)
                }
                previousChannels = currentChannels
            }
        }
    }

    fun isChannelAdded(name: String): Boolean = preferenceStore.channels.any { it.value.equals(name, ignoreCase = true) }

    fun addChannel(channel: UserName) {
        addChannels(listOf(channel))
    }

    fun addChannels(channels: List<UserName>) {
        val current = preferenceStore.channels
        val newChannels =
            channels
                .map(UserName::lowercase)
                .distinct()
                .filterNot(current::contains)
        if (newChannels.isEmpty()) return

        preferenceStore.channels = current + newChannels
        newChannels.forEach(chatRepository::joinChannel)
        chatChannelProvider.setActiveChannel(newChannels.last())
    }

    fun removeChannel(channel: UserName) {
        val wasActive = chatChannelProvider.activeChannel.value == channel
        preferenceStore.removeChannel(channel)
        chatRepository.updateChannels(preferenceStore.channels)
        channelDataCoordinator.cleanupChannel(channel)

        if (wasActive) {
            chatChannelProvider.setActiveChannel(preferenceStore.channels.firstOrNull())
        }
    }

    fun renameChannel(
        channel: UserName,
        displayName: String?,
    ) {
        val rename = displayName?.ifBlank { null }?.let { UserName(it) }
        preferenceStore.setRenamedChannel(ChannelWithRename(channel, rename))
    }

    fun retryChannelLoading(channel: UserName) {
        channelDataCoordinator.loadChannelData(channel)
    }

    fun reloadAllChannels() {
        channelDataCoordinator.reloadAllChannels()
    }

    fun reloadEmotes(channel: UserName) {
        channelDataCoordinator.loadChannelData(channel, forceNetwork = true)
        channelDataCoordinator.reloadUserEmotes()
    }

    fun reconnect() {
        chatConnector.reconnect()
    }

    fun blockChannel(channel: UserName) = viewModelScope.launch {
        runCatching {
            if (!preferenceStore.isLoggedIn) {
                return@launch
            }

            val channelId = channelRepository.getChannel(channel)?.id ?: return@launch
            ignoresRepository.addUserBlock(channelId, channel)
            removeChannel(channel)
        }
    }

    fun selectChannel(channel: UserName) {
        chatChannelProvider.setActiveChannel(channel)
        chatNotificationRepository.clearUnreadMessage(channel)
        chatNotificationRepository.clearMentionCount(channel)
    }

    fun applyChanges(updatedChannels: List<ChannelWithRename>) {
        val currentChannels = preferenceStore.channels
        val newChannelNames = updatedChannels.map { it.channel }
        val removedChannels = currentChannels - newChannelNames.toSet()

        // 1. Cleanup removed channels
        if (removedChannels.isNotEmpty()) {
            chatRepository.updateChannels(newChannelNames) // This handles join/part
            removedChannels.forEach { channel ->
                channelDataCoordinator.cleanupChannel(channel)
                // Remove rename
                preferenceStore.setRenamedChannel(ChannelWithRename(channel, null))
            }

            // 2. Update active channel if removed
            val activeChannel = chatChannelProvider.activeChannel.value
            if (activeChannel in removedChannels) {
                // Determine new active channel (try to keep index or go to first)
                // For simplicity, pick the first one, or null if empty
                val newActive = newChannelNames.firstOrNull()
                chatChannelProvider.setActiveChannel(newActive)
            }
        }

        // 3. Update order and list in preferences
        preferenceStore.channels = newChannelNames

        // 4. Apply renames
        updatedChannels.forEach {
            preferenceStore.setRenamedChannel(it)
        }
    }
}
