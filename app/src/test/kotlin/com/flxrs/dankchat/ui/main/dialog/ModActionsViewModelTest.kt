package com.flxrs.dankchat.ui.main.dialog

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.ShieldModeRepository
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.stream.StreamData
import com.flxrs.dankchat.data.repo.stream.StreamDataRepository
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.data.twitch.message.RoomStateTag
import com.flxrs.dankchat.preferences.stream.StreamsSettings
import com.flxrs.dankchat.preferences.stream.StreamsSettingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
internal class ModActionsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val channel = UserName("channel")
    private val channelId = UserId("1")
    private val roomStateFlow = MutableSharedFlow<RoomState>(replay = 1)
    private val shieldState = MutableStateFlow<Boolean?>(null)
    private val streamData = MutableStateFlow<ImmutableList<StreamData>>(persistentListOf())
    private val streamsSettings = MutableStateFlow(StreamsSettings())

    private val shieldModeRepository: ShieldModeRepository = mockk()
    private val channelRepository: ChannelRepository = mockk()
    private val streamDataRepository: StreamDataRepository = mockk()
    private val streamsSettingsDataStore: StreamsSettingsDataStore = mockk()
    private val authDataStore: AuthDataStore = mockk()

    private lateinit var viewModel: ModActionsViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { shieldModeRepository.getState(channel) } returns shieldState
        // The request stays unanswered, nothing may wait for it
        coEvery { shieldModeRepository.fetch(channel) } coAnswers { awaitCancellation() }
        every { channelRepository.getRoomState(channel) } answers { roomStateFlow.replayCache.firstOrNull() }
        every { channelRepository.getRoomStateFlow(channel) } returns roomStateFlow
        every { streamDataRepository.streamData } returns streamData
        every { streamsSettingsDataStore.currentSettings } returns streamsSettings
        every { streamsSettingsDataStore.fetchStreams } returns streamsSettings.map { it.fetchStreams }
        every { authDataStore.userIdString } returns channelId

        viewModel = ModActionsViewModel(shieldModeRepository, channelRepository, streamDataRepository, streamsSettingsDataStore, authDataStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opens with the cached shield mode while the request is running`() = runTest(testDispatcher) {
        shieldState.value = true

        viewModel.show(channel)

        assertEquals(true, viewModel.state.value?.shieldModeActive)
    }

    @Test
    fun `follows room state and shield mode while the request is running`() = runTest(testDispatcher) {
        viewModel.show(channel)
        assertNull(viewModel.state.value?.roomState)

        val roomState = RoomState(channel, channelId, tags = mapOf(RoomStateTag.SLOW to 30))
        roomStateFlow.emit(roomState)
        shieldState.value = false

        assertEquals(roomState, viewModel.state.value?.roomState)
        assertEquals(false, viewModel.state.value?.shieldModeActive)
    }

    @Test
    fun `nobody is the broadcaster without room state and user id`() = runTest(testDispatcher) {
        every { authDataStore.userIdString } returns null

        viewModel.show(channel)

        assertFalse(viewModel.state.value!!.isBroadcaster)
    }

    @Test
    fun `broadcaster is recognized once the room state arrives`() = runTest(testDispatcher) {
        viewModel.show(channel)
        assertFalse(viewModel.state.value!!.isBroadcaster)

        roomStateFlow.emit(RoomState(channel, channelId))

        assertTrue(viewModel.state.value!!.isBroadcaster)
    }

    @Test
    fun `channel is not live while only another channel streams`() = runTest(testDispatcher) {
        streamData.value = persistentListOf(StreamData(UserName("other"), formattedData = "", formattedDataCompact = ""))

        viewModel.show(channel)
        assertFalse(viewModel.state.value!!.isLive)

        streamData.value = persistentListOf(StreamData(channel, formattedData = "", formattedDataCompact = ""))
        assertTrue(viewModel.state.value!!.isLive)
    }

    @Test
    fun `channel counts as live while stream data is not fetched`() = runTest(testDispatcher) {
        streamsSettings.value = StreamsSettings(fetchStreams = false)

        viewModel.show(channel)

        assertTrue(viewModel.state.value!!.isLive)
    }

    @Test
    fun `dismiss closes the sheet and stops updates`() = runTest(testDispatcher) {
        viewModel.show(channel)
        viewModel.dismiss()

        shieldState.value = true

        assertNull(viewModel.state.value)
    }
}
