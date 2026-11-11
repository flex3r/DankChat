package com.flxrs.dankchat.domain

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.eventapi.EventSubManager
import com.flxrs.dankchat.data.repo.chat.ChannelMessageRateTracker
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.chat.ChatConnector
import com.flxrs.dankchat.data.repo.chat.ChatEventProcessor
import com.flxrs.dankchat.data.repo.chat.ChatMessageRepository
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.pubsub.PubSubManager
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.battery.BatterySettings
import com.flxrs.dankchat.preferences.battery.BatterySettingsDataStore
import com.flxrs.dankchat.preferences.chat.ChatSettings
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.utils.AppLifecycleListener
import com.flxrs.dankchat.utils.AppLifecycleListener.AppLifecycle
import io.mockk.coVerify
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
internal class BatterySaverCoordinatorTest {
    private val testDispatcher = StandardTestDispatcher()
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val immediate: CoroutineDispatcher = testDispatcher
        }

    private val appState = MutableStateFlow<AppLifecycle>(AppLifecycle.Foreground)
    private val channels = MutableStateFlow<ImmutableList<UserName>?>(persistentListOf(busyChannel, quietChannel))

    private val appLifecycleListener: AppLifecycleListener = mockk { every { appState } returns this@BatterySaverCoordinatorTest.appState }
    private val batterySettingsDataStore: BatterySettingsDataStore = mockk()
    private val chatSettingsDataStore: ChatSettingsDataStore = mockk { every { settings } returns flowOf(ChatSettings()) }
    private val messageRateTracker: ChannelMessageRateTracker = mockk()
    private val chatConnector: ChatConnector = mockk(relaxed = true)
    private val chatEventProcessor: ChatEventProcessor = mockk(relaxed = true)
    private val chatMessageRepository: ChatMessageRepository = mockk(relaxed = true)
    private val chatChannelProvider: ChatChannelProvider = mockk { every { channels } returns this@BatterySaverCoordinatorTest.channels }
    private val pubSubManager: PubSubManager = mockk(relaxed = true)
    private val eventSubManager: EventSubManager = mockk(relaxed = true)

    private lateinit var coordinator: BatterySaverCoordinator

    @BeforeEach
    fun setup() {
        every { batterySettingsDataStore.current() } returns BatterySettings()
        every { messageRateTracker.ratePerMinute(busyChannel) } returns 500
        every { messageRateTracker.ratePerMinute(quietChannel) } returns 5
        coordinator =
            BatterySaverCoordinator(
                appLifecycleListener = appLifecycleListener,
                batterySettingsDataStore = batterySettingsDataStore,
                chatSettingsDataStore = chatSettingsDataStore,
                messageRateTracker = messageRateTracker,
                chatConnector = chatConnector,
                chatEventProcessor = chatEventProcessor,
                chatMessageRepository = chatMessageRepository,
                chatChannelProvider = chatChannelProvider,
                pubSubManager = pubSubManager,
                eventSubManager = eventSubManager,
                dispatchersProvider = dispatchersProvider,
            )
    }

    @Test
    fun `nothing happens before the background delay elapses`() = runTest(testDispatcher) {
        coordinator.initialize()
        appState.value = AppLifecycle.Background
        advanceTimeBy(9.minutes)

        verify(exactly = 0) { chatConnector.partIrcChannel(any()) }
        verify(exactly = 0) { pubSubManager.pause() }
        endBackgroundSession()
    }

    @Test
    fun `busy channel is parted and event connections are paused after the delay`() = runTest(testDispatcher) {
        coordinator.initialize()
        appState.value = AppLifecycle.Background
        advanceTimeBy(11.minutes)

        verify { pubSubManager.pause() }
        coVerify { eventSubManager.pause() }
        verify { chatConnector.partIrcChannel(busyChannel) }
        verify(exactly = 0) { chatConnector.partIrcChannel(quietChannel) }
        endBackgroundSession()
    }

    @Test
    fun `channel turning busy later is parted on a subsequent evaluation`() = runTest(testDispatcher) {
        every { messageRateTracker.ratePerMinute(quietChannel) } returns 5
        coordinator.initialize()
        appState.value = AppLifecycle.Background
        advanceTimeBy(11.minutes)
        verify(exactly = 0) { chatConnector.partIrcChannel(quietChannel) }

        every { messageRateTracker.ratePerMinute(quietChannel) } returns 500
        advanceTimeBy(2.minutes)
        verify { chatConnector.partIrcChannel(quietChannel) }
        endBackgroundSession()
    }

    @Test
    fun `foreground before the delay cancels the saver session`() = runTest(testDispatcher) {
        coordinator.initialize()
        appState.value = AppLifecycle.Background
        advanceTimeBy(5.minutes)
        appState.value = AppLifecycle.Foreground
        advanceTimeBy(60.minutes)

        verify(exactly = 0) { chatConnector.partIrcChannel(any()) }
        verify(exactly = 0) { pubSubManager.pause() }
    }

    @Test
    fun `foreground resumes event connections and rejoins parted channels with backfill`() = runTest(testDispatcher) {
        coordinator.initialize()
        appState.value = AppLifecycle.Background
        advanceTimeBy(11.minutes)
        appState.value = AppLifecycle.Foreground
        runCurrent()

        verify { pubSubManager.resume() }
        verify { eventSubManager.resume() }
        verify { chatEventProcessor.rejoinChannelWithHistory(busyChannel) }
    }

    @Test
    fun `backfill is skipped when history on reconnect is disabled`() = runTest(testDispatcher) {
        every { chatSettingsDataStore.settings } returns flowOf(ChatSettings(loadMessageHistoryOnReconnect = false))
        coordinator.initialize()
        appState.value = AppLifecycle.Background
        advanceTimeBy(11.minutes)
        appState.value = AppLifecycle.Foreground
        runCurrent()

        verify { chatConnector.joinIrcChannel(busyChannel) }
        verify(exactly = 0) { chatEventProcessor.rejoinChannelWithHistory(any()) }
    }

    @Test
    fun `channels removed while parted are not rejoined`() = runTest(testDispatcher) {
        coordinator.initialize()
        appState.value = AppLifecycle.Background
        advanceTimeBy(11.minutes)
        channels.value = persistentListOf(quietChannel)
        appState.value = AppLifecycle.Foreground
        runCurrent()

        verify(exactly = 0) { chatConnector.joinIrcChannel(any()) }
        verify(exactly = 0) { chatEventProcessor.rejoinChannelWithHistory(any()) }
    }

    @Test
    fun `disabled settings do nothing in background`() = runTest(testDispatcher) {
        every { batterySettingsDataStore.current() } returns
            BatterySettings(partBusyChannels = false, pauseEventConnections = false)
        coordinator.initialize()
        appState.value = AppLifecycle.Background
        advanceTimeBy(60.minutes)

        verify(exactly = 0) { chatConnector.partIrcChannel(any()) }
        verify(exactly = 0) { pubSubManager.pause() }
    }

    @Test
    fun `parting disabled still pauses event connections`() = runTest(testDispatcher) {
        every { batterySettingsDataStore.current() } returns BatterySettings(partBusyChannels = false)
        coordinator.initialize()
        appState.value = AppLifecycle.Background
        advanceTimeBy(11.minutes)

        verify { pubSubManager.pause() }
        verify(exactly = 0) { chatConnector.partIrcChannel(any()) }
        endBackgroundSession()
    }

    // Cancels the coordinator's background evaluation loop, otherwise runTest advances the
    // virtual clock against the infinite loop forever during cleanup
    private fun TestScope.endBackgroundSession() {
        appState.value = AppLifecycle.Foreground
        runCurrent()
    }

    companion object {
        private val busyChannel = "forsen".toUserName()
        private val quietChannel = "quiet".toUserName()
    }
}
