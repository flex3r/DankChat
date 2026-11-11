package com.flxrs.dankchat.domain

import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.eventapi.EventSubManager
import com.flxrs.dankchat.data.repo.chat.ChannelMessageRateTracker
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.chat.ChatConnector
import com.flxrs.dankchat.data.repo.chat.ChatEventProcessor
import com.flxrs.dankchat.data.repo.chat.ChatMessageRepository
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.data.twitch.pubsub.PubSubManager
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.battery.BatterySettingsDataStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.utils.AppLifecycleListener
import com.flxrs.dankchat.utils.AppLifecycleListener.AppLifecycle
import com.flxrs.dankchat.utils.TextResource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger("BatterySaverCoordinator")

@Single
class BatterySaverCoordinator(
    private val appLifecycleListener: AppLifecycleListener,
    private val batterySettingsDataStore: BatterySettingsDataStore,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val messageRateTracker: ChannelMessageRateTracker,
    private val chatConnector: ChatConnector,
    private val chatEventProcessor: ChatEventProcessor,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatChannelProvider: ChatChannelProvider,
    private val pubSubManager: PubSubManager,
    private val eventSubManager: EventSubManager,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val partedChannels = mutableSetOf<UserName>()
    private var eventConnectionsPaused = false

    fun initialize() {
        scope.launch {
            appLifecycleListener.appState.collectLatest { state ->
                when (state) {
                    is AppLifecycle.Foreground -> onForeground()
                    is AppLifecycle.Background -> onBackground()
                }
            }
        }
    }

    private suspend fun onForeground() {
        if (eventConnectionsPaused) {
            eventConnectionsPaused = false
            pubSubManager.resume()
            eventSubManager.resume()
        }

        if (partedChannels.isEmpty()) {
            return
        }

        val currentChannels = chatChannelProvider.channels.value.orEmpty()
        val channelsToRejoin = partedChannels.filter { it in currentChannels }
        partedChannels.clear()

        val withHistory = chatSettingsDataStore.settings.first().loadMessageHistoryOnReconnect
        channelsToRejoin.forEach { channel ->
            logger.info { "Rejoining #$channel after battery saver part" }
            when {
                withHistory -> chatEventProcessor.rejoinChannelWithHistory(channel)
                else -> chatConnector.joinIrcChannel(channel)
            }
        }
    }

    private suspend fun onBackground() {
        val settings = batterySettingsDataStore.current()
        if (!settings.partBusyChannels && !settings.pauseEventConnections) {
            return
        }

        delay(settings.backgroundDelay.duration)

        if (settings.pauseEventConnections) {
            eventConnectionsPaused = true
            pubSubManager.pause()
            eventSubManager.pause()
        }

        if (!settings.partBusyChannels) {
            return
        }

        while (true) {
            partBusyChannels(settings.busyThreshold.messagesPerMinute)
            delay(EVALUATION_INTERVAL)
        }
    }

    private fun partBusyChannels(threshold: Int) {
        val channels = chatChannelProvider.channels.value.orEmpty()
        channels
            .filter { it !in partedChannels && messageRateTracker.ratePerMinute(it) >= threshold }
            .forEach { channel ->
                logger.info { "Parting #$channel to save battery, rate exceeded $threshold messages per minute" }
                partedChannels += channel
                chatConnector.partIrcChannel(channel)
                chatMessageRepository.addSystemMessage(channel, SystemMessageType.Custom(TextResource.Res(R.string.battery_saver_channel_paused)))
            }
    }

    companion object {
        private val EVALUATION_INTERVAL = 1.minutes
    }
}
