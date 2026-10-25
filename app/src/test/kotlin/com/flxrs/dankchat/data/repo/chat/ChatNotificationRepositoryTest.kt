package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatNotificationRepositoryTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val immediate: CoroutineDispatcher = testDispatcher
        }
    private val channel = "channel".toUserName()
    private val chatSettingsDataStore: ChatSettingsDataStore = mockk { every { debouncedScrollBack } returns flowOf(500) }
    private val chatChannelProvider: ChatChannelProvider = mockk { every { activeChannel } returns MutableStateFlow(null) }
    private val repository = ChatNotificationRepository(mockk(relaxed = true), chatSettingsDataStore, chatChannelProvider, dispatchersProvider)

    @Test
    fun `creating mention flows keeps existing counts`() {
        repository.incrementMentionCount(channel, 1)

        repository.createMentionFlows(channel)

        assertEquals(mapOf(WhisperMessage.WHISPER_CHANNEL to 0, channel to 1), repository.channelMentionCount.value)
    }

    @Test
    fun `clearing mention counts keeps the whisper mention count`() {
        repository.incrementMentionCount(WhisperMessage.WHISPER_CHANNEL, 2)
        repository.incrementMentionCount(channel, 1)

        repository.clearMentionCounts()

        assertEquals(mapOf(WhisperMessage.WHISPER_CHANNEL to 2, channel to 0), repository.channelMentionCount.value)
    }

}
