package com.flxrs.dankchat.domain

import app.cash.turbine.test
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.auth.StartupValidation
import com.flxrs.dankchat.data.auth.StartupValidationHolder
import com.flxrs.dankchat.data.repo.chat.ChatLoadingFailure
import com.flxrs.dankchat.data.repo.chat.ChatLoadingStep
import com.flxrs.dankchat.data.repo.chat.ChatMessageRepository
import com.flxrs.dankchat.data.repo.data.DataLoadingFailure
import com.flxrs.dankchat.data.repo.data.DataLoadingStep
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.data.repo.data.DataUpdateEventMessage
import com.flxrs.dankchat.data.state.ChannelLoadingFailure
import com.flxrs.dankchat.data.state.ChannelLoadingState
import com.flxrs.dankchat.data.state.GlobalLoadingState
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
internal class ChannelDataCoordinatorTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val immediate: CoroutineDispatcher = testDispatcher
        }

    private val channelDataLoader: ChannelDataLoader = mockk()
    private val globalDataLoader: GlobalDataLoader = mockk()
    private val chatMessageRepository: ChatMessageRepository = mockk(relaxed = true)
    private val dataRepository: DataRepository = mockk(relaxed = true)
    private val authDataStore: AuthDataStore = mockk()
    private val preferenceStore: DankChatPreferenceStore = mockk()
    private val startupValidationHolder = StartupValidationHolder()
    private val userBlocksGate: UserBlocksGate = mockk(relaxed = true)

    private val dataUpdateEvents = MutableSharedFlow<DataUpdateEventMessage>()
    private val dataLoadingFailures = MutableStateFlow<Set<DataLoadingFailure>>(emptySet())
    private val chatLoadingFailures = MutableStateFlow<Set<ChatLoadingFailure>>(emptySet())

    private lateinit var coordinator: ChannelDataCoordinator

    @BeforeEach
    fun setup() {
        every { dataRepository.dataUpdateEvents } returns dataUpdateEvents
        every { dataRepository.dataLoadingFailures } returns dataLoadingFailures
        every { chatMessageRepository.chatLoadingFailures } returns chatLoadingFailures

        startupValidationHolder.update(StartupValidation.Validated)

        coordinator =
            ChannelDataCoordinator(
                channelDataLoader = channelDataLoader,
                globalDataLoader = globalDataLoader,
                chatMessageRepository = chatMessageRepository,
                dataRepository = dataRepository,
                authDataStore = authDataStore,
                preferenceStore = preferenceStore,
                startupValidationHolder = startupValidationHolder,
                userBlocksGate = userBlocksGate,
                dispatchersProvider = dispatchersProvider,
            )
    }

    @Test
    fun `loadGlobalData transitions to Loaded when no failures`() = runTest(testDispatcher) {
        every { authDataStore.isLoggedIn } returns false
        coEvery { globalDataLoader.loadGlobalData() } returns emptyList()
        every { dataRepository.clearDataLoadingFailures() } just runs

        coordinator.loadGlobalData()

        assertEquals(GlobalLoadingState.Loaded, coordinator.globalLoadingState.value)
    }

    @Test
    fun `loadGlobalData transitions to Failed when data failures exist`() = runTest(testDispatcher) {
        every { authDataStore.isLoggedIn } returns false
        coEvery { globalDataLoader.loadGlobalData() } returns emptyList()
        every { dataRepository.clearDataLoadingFailures() } just runs

        val failure = DataLoadingFailure(DataLoadingStep.GlobalBTTVEmotes, RuntimeException("timeout"))
        dataLoadingFailures.value = setOf(failure)

        coordinator.loadGlobalData()

        val state = coordinator.globalLoadingState.value
        assertIs<GlobalLoadingState.Failed>(state)
        assertEquals(1, state.failures.size)
        assertEquals(DataLoadingStep.GlobalBTTVEmotes, state.failures.first().step)
    }

    @Test
    fun `loadGlobalData with auth loads auth global data`() = runTest(testDispatcher) {
        every { authDataStore.isLoggedIn } returns true
        every { authDataStore.userIdString } returns null
        coEvery { globalDataLoader.loadGlobalData() } returns emptyList()
        coEvery { globalDataLoader.loadAuthGlobalData() } returns emptyList()
        every { dataRepository.clearDataLoadingFailures() } just runs

        coordinator.loadGlobalData()

        coVerify { userBlocksGate.loadAndOpen() }
        coVerify { globalDataLoader.loadAuthGlobalData() }
    }

    @Test
    fun `user emote failure after the first page moves the state to Failed`() = runTest(testDispatcher) {
        val userId = UserId("1")
        val laterPages = CompletableDeferred<Unit>()
        val failure = DataLoadingFailure(DataLoadingStep.TwitchEmotes, RuntimeException("timeout"))
        every { authDataStore.isLoggedIn } returns true
        every { authDataStore.userIdString } returns userId
        coEvery { globalDataLoader.loadGlobalData() } returns emptyList()
        coEvery { globalDataLoader.loadAuthGlobalData() } returns emptyList()
        coEvery { globalDataLoader.loadUserEmotes(userId, any()) } coAnswers {
            secondArg<(() -> Unit)?>()?.invoke()
            laterPages.await()
            dataLoadingFailures.value = setOf(failure)
            Result.failure(failure.failure)
        }

        coordinator.loadGlobalData()
        assertEquals(GlobalLoadingState.Loaded, coordinator.globalLoadingState.value)

        laterPages.complete(Unit)
        assertEquals(GlobalLoadingState.Failed(failures = setOf(failure)), coordinator.globalLoadingState.value)
    }

    @Test
    fun `loadChannelData transitions to Loaded`() = runTest(testDispatcher) {
        val channel = UserName("testchannel")
        coEvery { channelDataLoader.loadChannelData(channel, any()) } returns ChannelLoadingState.Loaded

        coordinator.loadChannelData(channel)

        assertEquals(ChannelLoadingState.Loaded, coordinator.getChannelLoadingState(channel).value)
    }

    @Test
    fun `loadChannelData transitions to Failed on loader failure`() = runTest(testDispatcher) {
        val channel = UserName("testchannel")
        val failures = listOf(ChannelLoadingFailure.BTTVEmotes(channel, RuntimeException("network")))
        coEvery { channelDataLoader.loadChannelData(channel, any()) } returns ChannelLoadingState.Failed(failures)

        coordinator.loadChannelData(channel)

        val state = coordinator.getChannelLoadingState(channel).value
        assertIs<ChannelLoadingState.Failed>(state)
        assertEquals(1, state.failures.size)
    }

    @Test
    fun `chat loading failures update global state from Loaded to Failed`() = runTest(testDispatcher) {
        every { authDataStore.isLoggedIn } returns false
        coEvery { globalDataLoader.loadGlobalData() } returns emptyList()
        every { dataRepository.clearDataLoadingFailures() } just runs

        coordinator.loadGlobalData()
        assertEquals(GlobalLoadingState.Loaded, coordinator.globalLoadingState.value)

        coordinator.globalLoadingState.test {
            assertEquals(GlobalLoadingState.Loaded, awaitItem())

            val chatFailure = ChatLoadingFailure(ChatLoadingStep.RecentMessages(UserName("test")), RuntimeException("fail"))
            chatLoadingFailures.value = setOf(chatFailure)

            val failed = awaitItem()
            assertIs<GlobalLoadingState.Failed>(failed)
            assertEquals(1, failed.chatFailures.size)
        }
    }

    @Test
    fun `retryDataLoading retries failed global steps`() = runTest(testDispatcher) {
        every { dataRepository.clearDataLoadingFailures() } just runs
        every { chatMessageRepository.clearChatLoadingFailures() } just runs
        coEvery { globalDataLoader.loadGlobalBTTVEmotes() } returns Result.success(Unit)

        val failedState =
            GlobalLoadingState.Failed(
                failures = setOf(DataLoadingFailure(DataLoadingStep.GlobalBTTVEmotes, RuntimeException("timeout"))),
            )

        coordinator.retryDataLoading(failedState)

        coVerify { globalDataLoader.loadGlobalBTTVEmotes() }
    }

    @Test
    fun `retryDataLoading retries failed channel steps via channelDataLoader`() = runTest(testDispatcher) {
        val channel = UserName("testchannel")
        every { dataRepository.clearDataLoadingFailures() } just runs
        every { chatMessageRepository.clearChatLoadingFailures() } just runs
        coEvery { channelDataLoader.loadChannelData(channel, any()) } returns ChannelLoadingState.Loaded

        val failedState =
            GlobalLoadingState.Failed(
                failures = setOf(DataLoadingFailure(DataLoadingStep.ChannelBTTVEmotes(channel, DisplayName("testchannel"), UserId("123")), RuntimeException("fail"))),
            )

        coordinator.retryDataLoading(failedState)

        coVerify { channelDataLoader.loadChannelData(channel, any()) }
    }

    @Test
    fun `retryDataLoading retries failed chat steps`() = runTest(testDispatcher) {
        val channel = UserName("testchannel")
        every { dataRepository.clearDataLoadingFailures() } just runs
        every { chatMessageRepository.clearChatLoadingFailures() } just runs
        coEvery { channelDataLoader.loadChannelData(channel, any()) } returns ChannelLoadingState.Loaded

        val failedState =
            GlobalLoadingState.Failed(
                chatFailures = setOf(ChatLoadingFailure(ChatLoadingStep.RecentMessages(channel), RuntimeException("fail"))),
            )

        coordinator.retryDataLoading(failedState)

        coVerify { channelDataLoader.loadChannelData(channel, any()) }
    }

    @Test
    fun `retryDataLoading transitions to Loaded when retry succeeds`() = runTest(testDispatcher) {
        every { dataRepository.clearDataLoadingFailures() } just runs
        every { chatMessageRepository.clearChatLoadingFailures() } just runs
        coEvery { globalDataLoader.loadGlobalBTTVEmotes() } returns Result.success(Unit)

        val failedState =
            GlobalLoadingState.Failed(
                failures = setOf(DataLoadingFailure(DataLoadingStep.GlobalBTTVEmotes, RuntimeException("timeout"))),
            )

        coordinator.retryDataLoading(failedState)

        assertEquals(GlobalLoadingState.Loaded, coordinator.globalLoadingState.value)
    }

    @Test
    fun `retryDataLoading stays Failed when failures persist`() = runTest(testDispatcher) {
        val failure = DataLoadingFailure(DataLoadingStep.GlobalBTTVEmotes, RuntimeException("still broken"))
        every { dataRepository.clearDataLoadingFailures() } just runs
        every { chatMessageRepository.clearChatLoadingFailures() } just runs
        coEvery { globalDataLoader.loadGlobalBTTVEmotes() } returns Result.success(Unit)
        dataLoadingFailures.value = setOf(failure)

        val failedState = GlobalLoadingState.Failed(failures = setOf(failure))

        coordinator.retryDataLoading(failedState)

        assertIs<GlobalLoadingState.Failed>(coordinator.globalLoadingState.value)
    }

    @Test
    fun `cleanupChannel removes channel state`() = runTest(testDispatcher) {
        val channel = UserName("testchannel")
        coEvery { channelDataLoader.loadChannelData(channel, any()) } returns ChannelLoadingState.Loaded

        coordinator.loadChannelData(channel)
        assertEquals(ChannelLoadingState.Loaded, coordinator.getChannelLoadingState(channel).value)

        coordinator.cleanupChannel(channel)

        assertEquals(ChannelLoadingState.Idle, coordinator.getChannelLoadingState(channel).value)
    }
}
