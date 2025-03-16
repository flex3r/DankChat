package com.flxrs.dankchat.data.api.eventapi

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.chat.UserStateRepository
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Singleton

@Singleton
class EventSubManager(
    private val eventSubClient: EventSubClient,
    private val channelRepository: ChannelRepository,
    private val userStateRepository: UserStateRepository,
    private val preferenceStore: DankChatPreferenceStore,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)

    val events = eventSubClient.events

    init {
        scope.launch {
            userStateRepository.userState.map { it.moderationChannels }.collect {
                val userId = preferenceStore.userIdString ?: return@collect
                val channels = channelRepository.getChannels(it)
                channels.forEach {
                    val topic = EventSubTopic.ChannelModerate(channel = it.name, broadcasterId = it.id, moderatorId = userId)
                    eventSubClient.subscribe(topic)
                }
            }
        }
    }

    fun removeChannel(channel: UserName) {
        scope.launch {
            val topic = eventSubClient.topics.value
                .find { it.topic is EventSubTopic.ChannelModerate && it.topic.channel == channel }
                ?: return@launch
            eventSubClient.unsubscribe(topic)
        }
    }

    fun reconnect() {
        eventSubClient.reconnect()
    }

    fun reconnectIfNecessary() {
        eventSubClient.reconnectIfNecessary()
    }

    suspend fun close() {
        eventSubClient.closeAndClearTopics()
    }
}
