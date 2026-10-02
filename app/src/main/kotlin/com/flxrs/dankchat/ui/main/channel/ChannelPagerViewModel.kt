package com.flxrs.dankchat.ui.main.channel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.chat.canReplyTo
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.chat.ChatMessageRepository
import com.flxrs.dankchat.data.repo.chat.ChatNotificationRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class ChannelPagerViewModel(
    private val chatChannelProvider: ChatChannelProvider,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatNotificationRepository: ChatNotificationRepository,
    private val preferenceStore: DankChatPreferenceStore,
) : ViewModel() {
    val uiState: StateFlow<ChannelPagerUiState> =
        combine(
            preferenceStore.getChannelsWithRenamesFlow(),
            chatChannelProvider.activeChannel,
        ) { channels, active ->
            ChannelPagerUiState(
                channels = channels.map { it.channel }.toImmutableList(),
                currentPage =
                    channels
                        .indexOfFirst { it.channel == active }
                        .coerceAtLeast(0),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChannelPagerUiState())

    fun onPageChanged(page: Int) {
        setActivePage(page)
        clearNotifications(page)
    }

    fun setActivePage(page: Int) {
        val channels = preferenceStore.channels
        if (page in channels.indices) {
            chatChannelProvider.setActiveChannel(channels[page])
        }
    }

    fun clearNotifications(page: Int) {
        val channels = preferenceStore.channels
        if (page in channels.indices) {
            chatNotificationRepository.clearUnreadMessage(channels[page])
            chatNotificationRepository.clearMentionCount(channels[page])
        }
    }

    fun canReplyTo(
        channel: UserName,
        messageId: String,
    ): Boolean = chatMessageRepository.getChat(channel).value.canReplyTo(messageId)

    /**
     * Validates that the message exists in the channel's chat and returns the jump target,
     * or null if the message can't be found.
     */
    fun resolveJumpTarget(
        channel: UserName,
        messageId: String,
    ): JumpTarget? {
        val channels = preferenceStore.channels
        val index = channels.indexOfFirst { it == channel }
        if (index < 0) return null
        if (chatMessageRepository.getChat(channel).value.none { it.message.id == messageId }) return null
        onPageChanged(index)
        return JumpTarget(channelIndex = index, channel = channel, messageId = messageId)
    }
}

@Immutable
data class JumpTarget(
    val channelIndex: Int,
    val channel: UserName,
    val messageId: String,
)

@Immutable
data class ChannelPagerUiState(
    val channels: ImmutableList<UserName> = persistentListOf(),
    val currentPage: Int = 0,
)
