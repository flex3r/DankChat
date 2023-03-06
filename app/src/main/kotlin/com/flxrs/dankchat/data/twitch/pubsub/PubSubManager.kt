package com.flxrs.dankchat.data.twitch.pubsub

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.withoutOAuthPrefix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject

class PubSubManager @Inject constructor(
    private val helixApiClient: HelixApiClient,
    private val preferenceStore: DankChatPreferenceStore,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val json: Json,
) {

    private val connections = mutableListOf<PubSubConnection>()
    private val collectJobs = mutableListOf<Job>()
    private val receiveChannel = Channel<PubSubMessage>(capacity = Channel.BUFFERED)

    val messages = receiveChannel.receiveAsFlow().shareIn(scope, started = SharingStarted.Eagerly)
    val connected: Boolean
        get() = connections.any { it.connected }

    val connectedAndHasWhisperTopic: Boolean
        get() = connections.any { it.connected && it.hasWhisperTopic }

    fun start() {
        if (!preferenceStore.isLoggedIn) {
            return
        }

        val userId = preferenceStore.userIdString ?: return
        val channels = preferenceStore.getChannels()

        scope.launch {
            val helixChannels = helixApiClient.getUsersByNames(channels)
                .getOrNull() ?: return@launch

            val topics = buildSet {
                add(PubSubTopic.Whispers(userId))
                helixChannels.forEach {
                    add(PubSubTopic.PointRedemptions(channelId = it.id, channelName = it.name))
                    add(PubSubTopic.ModeratorActions(userId = userId, channelId = it.id))
                }
            }
            listen(topics)
        }
    }

    fun reconnect() = resetCollectionWith { reconnect() }

    fun reconnectIfNecessary() = resetCollectionWith { reconnectIfNecessary() }

    fun close() = scope.launch {
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()
        connections.forEach { it.close() }
    }

    fun addChannel(channel: UserName) = scope.launch {
        if (!preferenceStore.isLoggedIn) {
            return@launch
        }

        val userId = preferenceStore.userIdString ?: return@launch
        val channelId = helixApiClient.getUserIdByName(channel)
            .getOrNull() ?: return@launch

        val pointRedemptions = PubSubTopic.PointRedemptions(channelId, channel)
        val moderatorActions = PubSubTopic.ModeratorActions(userId, channelId)
        listen(setOf(pointRedemptions, moderatorActions))
    }

    fun removeChannel(channel: UserName) {
        val emptyConnections = connections
            .onEach { it.unlistenByChannel(channel) }
            .filterNot { it.hasTopics }
            .onEach { it.close() }

        if (emptyConnections.isEmpty()) {
            return
        }

        connections.removeAll { conn -> emptyConnections.any { it.tag == conn.tag } }
        resetCollectionWith()
    }

    private fun listen(topics: Set<PubSubTopic>) {
        val oAuth = preferenceStore.oAuthKey?.withoutOAuthPrefix ?: return
        val remainingTopics = connections.fold(topics) { acc, conn ->
            conn.listen(acc)
        }

        if (remainingTopics.isEmpty() || connections.size >= PubSubConnection.MAX_CONNECTIONS) {
            return
        }

        scope.launch {
            remainingTopics
                .chunked(PubSubConnection.MAX_TOPICS)
                .withIndex()
                .takeWhile { (idx, _) -> connections.size + idx + 1 <= PubSubConnection.MAX_CONNECTIONS }
                .forEach { (_, topics) ->
                    val connection = PubSubConnection(
                        tag = "#${connections.size}",
                        client = client,
                        scope = this,
                        oAuth = oAuth,
                        jsonFormat = json,
                    )
                    connection.connect(initialTopics = topics.toSet())
                    connections += connection
                    collectJobs += launch { connection.collectEvents() }
                }
        }
    }

    private fun resetCollectionWith(action: PubSubConnection.() -> Unit = {}) = scope.launch {
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()
        collectJobs.addAll(
            elements = connections
                .map { conn ->
                    conn.action()
                    launch { conn.collectEvents() }
                }
        )
    }

    private suspend fun PubSubConnection.collectEvents() {
        events.collect {
            when (it) {
                is PubSubEvent.Message -> receiveChannel.send(it.message)
                else                   -> Unit
            }
        }
    }

    companion object {
        private val TAG = PubSubManager::class.java.simpleName
    }
}
