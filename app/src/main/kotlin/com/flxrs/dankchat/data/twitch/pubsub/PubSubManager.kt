package com.flxrs.dankchat.data.twitch.pubsub

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.auth.StartupValidationHolder
import com.flxrs.dankchat.data.repo.channel.Channel
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import com.flxrs.dankchat.utils.ForegroundServiceState
import com.flxrs.dankchat.utils.extensions.withoutOAuthPrefix
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.channels.Channel as CoroutineChannel

private val logger = KotlinLogging.logger("PubSubManager")

@Single
class PubSubManager(
    private val channelRepository: ChannelRepository,
    private val chatChannelProvider: ChatChannelProvider,
    private val developerSettingsDataStore: DeveloperSettingsDataStore,
    private val authDataStore: AuthDataStore,
    private val startupValidationHolder: StartupValidationHolder,
    private val foregroundServiceState: ForegroundServiceState,
    httpClient: HttpClient,
    private val json: Json,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val client =
        httpClient.config {
            install(WebSockets)
        }

    // guards connections, collectJobs and connectedIdentity, all mutations run on the scope with the mutex held
    private val mutex = Mutex()
    private val connections = CopyOnWriteArrayList<PubSubConnection>()
    private val collectJobs = mutableListOf<Job>()
    private var connectedIdentity: Identity? = null
    private val receiveChannel = CoroutineChannel<PubSubMessage>(capacity = CoroutineChannel.BUFFERED)

    val messages = receiveChannel.receiveAsFlow().shareIn(scope, started = SharingStarted.Eagerly)
    val connected: Boolean
        get() = connections.any { it.connected }

    private val pausedInBackground = MutableStateFlow(false)

    init {
        scope.launch {
            startupValidationHolder.awaitResolved()
            combine(
                authDataStore.settings.map { Triple(it.isLoggedIn, it.userId, it.oAuthKey) }.distinctUntilChanged(),
                chatChannelProvider.channels.filterNotNull(),
                developerSettingsDataStore.settings.map { it.shouldUsePubSub }.distinctUntilChanged(),
                pausedInBackground,
            ) { (isLoggedIn, userId, oAuthKey), channels, shouldUsePubSub, paused ->
                ConnectionParams(
                    identity =
                        when {
                            isLoggedIn && !paused && userId != null && oAuthKey != null -> Identity(userId, oAuthKey.withoutOAuthPrefix)
                            else -> null
                        },
                    channels = channels,
                    shouldUsePubSub = shouldUsePubSub,
                )
            }.collect { (identity, channels, shouldUsePubSub) ->
                mutex.withLock {
                    if (identity == null) {
                        closeAll()
                        logger.debug { "[PubSub] skipping connection, not logged in or paused" }
                        return@withLock
                    }

                    val resolved = channelRepository.getChannels(channels)
                    val topics = buildTopics(identity.userId, resolved, shouldUsePubSub)
                    when (identity) {
                        // Connections stay open across channel changes, only the changed topics are sent
                        connectedIdentity -> {
                            updateTopics(topics, identity.oAuth)
                        }

                        else -> {
                            closeAll()
                            logger.info { "[PubSub] rebuilding connections for ${resolved.size} channels, ${topics.size} topics (pubsub=$shouldUsePubSub)" }
                            listen(topics, identity.oAuth)
                            connectedIdentity = identity
                        }
                    }
                }
            }
        }
    }

    fun pause() {
        pausedInBackground.value = true
    }

    fun resume() {
        pausedInBackground.value = false
    }

    fun reconnect() = resetCollectionWith { reconnect() }

    fun reconnectIfNecessary() = resetCollectionWith { reconnectIfNecessary() }

    private fun buildTopics(
        userId: String,
        channels: List<Channel>,
        shouldUsePubSub: Boolean,
    ): Set<PubSubTopic> = buildSet {
        val uid = userId.toUserId()
        for (channel in channels) {
            add(PubSubTopic.PointRedemptions(channelId = channel.id, channelName = channel.name))
            add(PubSubTopic.PinnedChatUpdates(channelId = channel.id, channelName = channel.name))
            if (shouldUsePubSub) {
                add(PubSubTopic.ModeratorActions(userId = uid, channelId = channel.id, channelName = channel.name))
            }
        }
    }

    private fun updateTopics(
        topics: Set<PubSubTopic>,
        oAuth: String,
    ) {
        val listenedTopics = connections.flatMap { it.listenedTopics }.toSet()
        val removedTopics = listenedTopics - topics
        val addedTopics = topics - listenedTopics
        if (removedTopics.isEmpty() && addedTopics.isEmpty()) {
            return
        }

        logger.info { "[PubSub] updating topics, ${addedTopics.size} added, ${removedTopics.size} removed" }
        if (removedTopics.isNotEmpty()) {
            connections.forEach { it.unlisten(removedTopics) }
            val emptyConnections = connections.filterNot { it.hasTopics }
            if (emptyConnections.isNotEmpty()) {
                emptyConnections.forEach { it.close() }
                connections.removeAll(emptyConnections.toSet())
                resetCollection()
            }
        }

        if (addedTopics.isNotEmpty()) {
            listen(addedTopics, oAuth)
        }
    }

    private fun listen(
        topics: Set<PubSubTopic>,
        oAuth: String,
    ) {
        val remainingTopics =
            connections.fold(topics) { acc, conn ->
                conn.listen(acc)
            }

        if (remainingTopics.isEmpty() || connections.size >= PubSubConnection.MAX_CONNECTIONS) {
            return
        }

        remainingTopics
            .chunked(PubSubConnection.MAX_TOPICS)
            .withIndex()
            .takeWhile { (idx, _) -> connections.size + idx + 1 <= PubSubConnection.MAX_CONNECTIONS }
            .forEach { (_, chunk) ->
                val connection =
                    PubSubConnection(
                        tag = "#${connections.size}",
                        client = client,
                        scope = scope,
                        oAuth = oAuth,
                        jsonFormat = json,
                        serviceActive = foregroundServiceState.active,
                    )
                connection.connect(initialTopics = chunk.toSet())
                connections += connection
                collectJobs += scope.launch { connection.collectEvents() }
            }
    }

    private data class Identity(
        val userId: String,
        val oAuth: String,
    )

    private data class ConnectionParams(
        val identity: Identity?,
        val channels: List<UserName>,
        val shouldUsePubSub: Boolean,
    )

    private fun closeAll() {
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()
        connections.forEach { it.close() }
        connections.clear()
        connectedIdentity = null
    }

    private fun resetCollectionWith(action: PubSubConnection.() -> Unit = {}) = scope.launch {
        mutex.withLock {
            resetCollection(action)
        }
    }

    private fun resetCollection(action: PubSubConnection.() -> Unit = {}) {
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()
        collectJobs.addAll(
            elements =
                connections
                    .map { conn ->
                        conn.action()
                        scope.launch { conn.collectEvents() }
                    },
        )
    }

    private suspend fun PubSubConnection.collectEvents() {
        events.collect {
            when (it) {
                is PubSubEvent.Message -> receiveChannel.send(it.message)
                else -> Unit
            }
        }
    }
}
