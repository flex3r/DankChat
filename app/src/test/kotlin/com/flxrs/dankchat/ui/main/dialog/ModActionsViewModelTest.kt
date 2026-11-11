package com.flxrs.dankchat.ui.main.dialog

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.ShieldModeRepository
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.data.twitch.message.RoomStateTag
import io.mockk.coEvery
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val shieldModeRepository: ShieldModeRepository = mockk()
    private val channelRepository: ChannelRepository = mockk()
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
        every { authDataStore.userIdString } returns channelId

        viewModel = ModActionsViewModel(shieldModeRepository, channelRepository, authDataStore)
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
    fun `dismiss closes the sheet and stops updates`() = runTest(testDispatcher) {
        viewModel.show(channel)
        viewModel.dismiss()

        shieldState.value = true

        assertNull(viewModel.state.value)
    }
}
