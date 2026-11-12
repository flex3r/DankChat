package com.flxrs.dankchat.ui.main.input

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.chat.ChatConnector
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.chat.UserState
import com.flxrs.dankchat.data.repo.chat.UserStateRepository
import com.flxrs.dankchat.data.repo.command.CommandRepository
import com.flxrs.dankchat.data.repo.command.CommandResult
import com.flxrs.dankchat.data.twitch.chat.ConnectionState
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.ui.main.sheet.FullScreenSheetState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatInputViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val channel = UserName("channel")
    private val activeChannel = MutableStateFlow<UserName?>(channel)
    private val connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    private val sheetState = MutableStateFlow<FullScreenSheetState>(FullScreenSheetState.Closed)
    private val savedStateHandle = SavedStateHandle()

    private val chatRepository: ChatRepository = mockk(relaxed = true)
    private val chatChannelProvider: ChatChannelProvider = mockk()
    private val chatConnector: ChatConnector = mockk()
    private val commandRepository: CommandRepository = mockk()
    private val channelRepository: ChannelRepository = mockk()
    private val userStateRepository: UserStateRepository = mockk()
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore = mockk(relaxed = true)
    private val dispatchersProvider: DispatchersProvider = mockk()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { chatChannelProvider.activeChannel } returns activeChannel
        every { chatConnector.getConnectionState(channel) } returns connectionState
        every { chatRepository.lastReceivedWhisperUser } returns MutableStateFlow(null)
        every { channelRepository.getRoomState(channel) } returns RoomState(channel = channel, channelId = UserId("1"))
        every { userStateRepository.userState } returns MutableStateFlow(UserState())
        every { userStateRepository.getSendDelay(channel) } returns 1.seconds
        every { dispatchersProvider.default } returns testDispatcher
        coEvery { commandRepository.checkForCommands(any(), channel, any(), any(), any()) } returns CommandResult.NotFound
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `draft is restored from saved state`() {
        savedStateHandle[DRAFT_KEY] = "forsenE"

        val viewModel = createViewModel()

        assertEquals(expected = "forsenE", actual = viewModel.textFieldState.text.toString())
    }

    @Test
    fun `typed text is saved as draft`() {
        val viewModel = createViewModel()

        viewModel.type("forsenE")

        assertEquals(expected = "forsenE", actual = savedStateHandle[DRAFT_KEY])
    }

    @Test
    fun `whisper text is not saved as draft`() {
        val viewModel = createViewModel()
        viewModel.type("forsenE")

        viewModel.setWhisperTarget(UserName("user"))
        viewModel.type("private")

        assertEquals(expected = "", actual = savedStateHandle[DRAFT_KEY])
    }

    private fun createViewModel(): ChatInputViewModel {
        val viewModel =
            ChatInputViewModel(
                chatRepository = chatRepository,
                chatChannelProvider = chatChannelProvider,
                chatConnector = chatConnector,
                commandRepository = commandRepository,
                channelRepository = channelRepository,
                userStateRepository = userStateRepository,
                suggestionProvider = mockk(),
                preferenceStore = mockk(relaxed = true),
                chatSettingsDataStore = mockk(relaxed = true),
                appearanceSettingsDataStore = appearanceSettingsDataStore,
                notificationsSettingsDataStore = mockk(),
                emoteRepository = mockk(),
                emoteUsageRepository = mockk(),
                streamsSettingsDataStore = mockk(relaxed = true),
                streamDataRepository = mockk(relaxed = true),
                dispatchersProvider = dispatchersProvider,
                savedStateHandle = savedStateHandle,
            )
        viewModel.uiState(externalSheetState = sheetState, externalMentionTab = MutableStateFlow(0))
        return viewModel
    }

    // Snapshot changes only reach the view model's text flows once they are applied
    private fun ChatInputViewModel.type(text: String) {
        textFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private companion object {
        const val DRAFT_KEY = "draft"
    }
}
