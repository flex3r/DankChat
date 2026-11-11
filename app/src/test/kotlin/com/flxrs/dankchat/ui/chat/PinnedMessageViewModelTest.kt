package com.flxrs.dankchat.ui.chat

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.repo.PinnedMessage
import com.flxrs.dankchat.data.repo.PinnedMessageRepository
import com.flxrs.dankchat.data.repo.PinnedMessageState
import com.flxrs.dankchat.data.repo.chat.ChatMessageRepository
import com.flxrs.dankchat.data.repo.chat.ChatNotificationRepository
import com.flxrs.dankchat.data.repo.chat.MessageProcessor
import com.flxrs.dankchat.data.repo.chat.UserState
import com.flxrs.dankchat.data.repo.chat.UserStateRepository
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.chat.ChatSettings
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
internal class PinnedMessageViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val channel = UserName("channel")
    private val pinState = MutableStateFlow<PinnedMessageState>(PinnedMessageState.None)
    private val chatSettings = MutableStateFlow(ChatSettings())
    private val whispers = MutableStateFlow<ImmutableList<ChatItem>>(persistentListOf())
    private val messageUi: ChatMessageUiState.PrivMessageUi = mockk()

    private val pinnedMessageRepository: PinnedMessageRepository = mockk()
    private val chatMessageRepository: ChatMessageRepository = mockk()
    private val chatNotificationRepository: ChatNotificationRepository = mockk()
    private val messageProcessor: MessageProcessor = mockk()
    private val chatMessageMapper: ChatMessageMapper = mockk()
    private val preferenceStore: DankChatPreferenceStore = mockk()
    private val usersRepository: UsersRepository = mockk()
    private val userStateRepository: UserStateRepository = mockk()
    private val chatSettingsDataStore: ChatSettingsDataStore = mockk()

    private lateinit var viewModel: PinnedMessageViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        val message = PrivMessage(
            id = MESSAGE_ID,
            channel = channel,
            sourceChannel = null,
            name = UserName("sender"),
            displayName = DisplayName("Sender"),
            message = "pinned",
            tags = emptyMap(),
        )
        every { pinnedMessageRepository.getState(channel) } returns pinState
        every { chatMessageRepository.findMessage(MESSAGE_ID, channel, whispers) } returns message
        every { chatNotificationRepository.whispers } returns whispers
        every { userStateRepository.userState } returns MutableStateFlow(UserState())
        every { chatSettingsDataStore.settings } returns chatSettings
        every { chatSettingsDataStore.currentSettings } returns chatSettings
        every { chatMessageMapper.mapToUiState(any(), any(), any(), any()) } returns messageUi

        viewModel =
            PinnedMessageViewModel(
                channel = channel,
                pinnedMessageRepository = pinnedMessageRepository,
                chatMessageRepository = chatMessageRepository,
                chatNotificationRepository = chatNotificationRepository,
                messageProcessor = messageProcessor,
                chatMessageMapper = chatMessageMapper,
                preferenceStore = preferenceStore,
                usersRepository = usersRepository,
                userStateRepository = userStateRepository,
                chatSettingsDataStore = chatSettingsDataStore,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `timed pin keeps one expanded state while the countdown runs`() = runTest(testDispatcher) {
        val states = mutableListOf<PinnedMessageUiState>()
        backgroundScope.launch { viewModel.uiState.collect { states += it } }
        collectRemainingTime()

        pinState.value = PinnedMessageState.Pinned(pin(endsAt = Clock.System.now() + 10.minutes))
        advanceTimeBy(5.seconds)

        assertEquals(expected = 1, actual = states.count { it is PinnedMessageUiState.Expanded })
        assertNotNull(viewModel.remainingTime.value)
    }

    @Test
    fun `pin without an end has no countdown`() = runTest(testDispatcher) {
        collectUiState()
        collectRemainingTime()

        pinState.value = PinnedMessageState.Pinned(pin(endsAt = null))

        assertIs<PinnedMessageUiState.Expanded>(viewModel.uiState.value)
        assertNull(viewModel.remainingTime.value)
    }

    @Test
    fun `countdown of a removed pin stays until the next pin`() = runTest(testDispatcher) {
        collectRemainingTime()

        pinState.value = PinnedMessageState.Pinned(pin(endsAt = Clock.System.now() + 10.minutes))
        pinState.value = PinnedMessageState.None
        assertNotNull(viewModel.remainingTime.value)

        pinState.value = PinnedMessageState.Pinned(pin(endsAt = null))
        assertNull(viewModel.remainingTime.value)
    }

    private fun TestScope.collectUiState() {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private fun TestScope.collectRemainingTime() {
        backgroundScope.launch { viewModel.remainingTime.collect {} }
    }

    private fun pin(endsAt: Instant?): PinnedMessage = PinnedMessage(
        messageId = MESSAGE_ID,
        channel = channel,
        text = "pinned",
        emotesWithPositions = emptyList(),
        senderId = UserId("1"),
        senderLogin = UserName("sender"),
        senderName = DisplayName("Sender"),
        pinnedByName = DisplayName("Mod"),
        startsAt = Clock.System.now(),
        endsAt = endsAt,
    )

    private companion object {
        const val MESSAGE_ID = "message-id"
    }
}
