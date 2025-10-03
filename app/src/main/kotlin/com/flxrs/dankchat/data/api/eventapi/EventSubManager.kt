package com.flxrs.dankchat.data.api.eventapi

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.chat.UserStateRepository
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single
class EventSubManager(
    private val eventSubClient: EventSubClient,
    private val channelRepository: ChannelRepository,
    private val userStateRepository: UserStateRepository,
    private val preferenceStore: DankChatPreferenceStore,
    developerSettingsDataStore: DeveloperSettingsDataStore,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val isEnabled = developerSettingsDataStore.current().shouldUseEventSub
    private var debugOutput = developerSettingsDataStore.current().eventSubDebugOutput

    val events = eventSubClient.events
        .filter { it !is SystemMessage || debugOutput }

    init {
        scope.launch {
            if (!isEnabled) {
                return@launch
            }

            userStateRepository.userState.map { it.moderationChannels }.collect {
                val userId = preferenceStore.userIdString ?: return@collect
                val channels = channelRepository.getChannels(it)
                channels.forEach {
                    val topic = EventSubTopic.ChannelModerate(channel = it.name, broadcasterId = it.id, moderatorId = userId)
                    eventSubClient.subscribe(topic)
                }
            }
        }

        scope.launch {
            if (!isEnabled) {
                return@launch
            }

            developerSettingsDataStore.settings.collect {
                debugOutput = it.eventSubDebugOutput
            }
        }
    }

    fun connectedAndHasModerateTopic(channel: UserName): Boolean {
        val topics = eventSubClient.topics.value
        return eventSubClient.connected && topics.isNotEmpty() && topics.any {
            it.topic is EventSubTopic.ChannelModerate && it.topic.channel == channel
        }
    }

    fun removeChannel(channel: UserName) {
        if (!isEnabled) {
            return
        }

        scope.launch {
            val topic = eventSubClient.topics.value
                .find { it.topic is EventSubTopic.ChannelModerate && it.topic.channel == channel }
                ?: return@launch
            eventSubClient.unsubscribe(topic)
        }
    }

    fun reconnect() {
        if (!isEnabled) {
            return
        }

        eventSubClient.reconnect()
    }

    fun reconnectIfNecessary() {
        if (!isEnabled) {
            return
        }

        eventSubClient.reconnectIfNecessary()
    }

    suspend fun close() {
        if (!isEnabled) {
            return
        }

        eventSubClient.closeAndClearTopics()
    }
}
