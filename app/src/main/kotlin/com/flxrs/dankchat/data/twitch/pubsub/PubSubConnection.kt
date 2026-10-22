package com.flxrs.dankchat.data.twitch.pubsub

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.ifBlank
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.twitch.pubsub.dto.PubSubDataMessage
import com.flxrs.dankchat.data.twitch.pubsub.dto.moderation.ModerationActionData
import com.flxrs.dankchat.data.twitch.pubsub.dto.moderation.ModerationActionType
import com.flxrs.dankchat.data.twitch.pubsub.dto.moderation.ModeratorAddedData
import com.flxrs.dankchat.data.twitch.pubsub.dto.redemption.PointRedemption
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import com.flxrs.dankchat.utils.extensions.timer
import com.flxrs.dankchat.utils.webSocketCoroutineExceptionHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.util.collections.ConcurrentSet
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val logger = KotlinLogging.logger("PubSubConnection")

@OptIn(DelicateCoroutinesApi::class)
class PubSubConnection(
    val tag: String,
    private val client: HttpClient,
    private val scope: CoroutineScope,
    private val oAuth: String,
    private val jsonFormat: Json,
    private val serviceActive: StateFlow<Boolean>,
) {
    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    private val receiveChannel = Channel<PubSubEvent>(capacity = Channel.BUFFERED)

    private var awaitingPong = false

    // mutated only via PubSubManager (serialized by its mutex), but read concurrently by the message loop
    private val topics = ConcurrentSet<PubSubTopic>()
    private lateinit var currentOAuth: String
    private val canAcceptTopics: Boolean
        get() = connected && topics.size < MAX_TOPICS

    val connected: Boolean
        get() = session?.isActive == true && session?.incoming?.isClosedForReceive == false

    val hasTopics: Boolean
        get() = topics.isNotEmpty()
    val events =
        receiveChannel.receiveAsFlow().distinctUntilChanged { old, new ->
            (old.isDisconnected && new.isDisconnected) || old == new
        }

    fun connect(initialTopics: Set<PubSubTopic>): Set<PubSubTopic> {
        if (connected || connectionJob?.isActive == true) {
            return initialTopics
        }

        currentOAuth = oAuth
        awaitingPong = false

        val (possibleTopics, remainingTopics) = initialTopics.splitAt(MAX_TOPICS)
        topics.clear()
        topics.addAll(possibleTopics)

        logger.info { "[PubSub $tag] connecting with ${possibleTopics.size} topics" }
        connectionJob =
            scope.launch(webSocketCoroutineExceptionHandler("PubSub $tag")) {
                var retryCount = 1
                while (retryCount <= RECONNECT_MAX_ATTEMPTS) {
                    var serverRequestedReconnect = false
                    try {
                        client.webSocket(PUBSUB_URL) {
                            session = this
                            retryCount = 1
                            receiveChannel.trySend(PubSubEvent.Connected)
                            logger.info { "[PubSub $tag] connected" }

                            possibleTopics
                                .toRequestMessages()
                                .forEach { send(Frame.Text(it)) }
                            logger.debug { "[PubSub $tag] sent LISTEN for ${possibleTopics.size} topics" }

                            // PubSub doesn't replay missed pin events, so re-sync pins on every (re)connect
                            possibleTopics
                                .filterIsInstance<PubSubTopic.PinnedChatUpdates>()
                                .forEach { topic ->
                                    val resync = PubSubMessage.PinnedChatUpdate(channelName = topic.channelName, removed = false)
                                    receiveChannel.trySend(PubSubEvent.Message(resync))
                                }

                            var pingJob: Job? = null
                            try {
                                pingJob = setupPingInterval()

                                while (isActive) {
                                    val result = incoming.receiveCatching()
                                    val text =
                                        when (val frame = result.getOrNull()) {
                                            null -> {
                                                val cause = result.exceptionOrNull() ?: return@webSocket
                                                throw cause
                                            }

                                            else -> {
                                                (frame as? Frame.Text)?.readText() ?: continue
                                            }
                                        }

                                    serverRequestedReconnect = handleMessage(text)
                                    if (serverRequestedReconnect) return@webSocket
                                }
                            } finally {
                                pingJob?.cancel()
                            }
                        }

                        session = null
                        receiveChannel.trySend(PubSubEvent.Closed)

                        if (!serverRequestedReconnect) {
                            logger.info { "[PubSub $tag] connection closed" }
                            return@launch
                        }
                        logger.info { "[PubSub $tag] reconnecting after server request" }
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        logger.error { "[PubSub $tag] connection failed: $t" }
                        session = null
                        receiveChannel.trySend(PubSubEvent.Closed)

                        if (!serviceActive.value) {
                            logger.info { "[PubSub $tag] foreground service inactive, not retrying" }
                            return@launch
                        }

                        logger.error { "[PubSub $tag] attempting to reconnect #$retryCount.." }
                        val jitter = randomJitter()
                        val reconnectDelay = RECONNECT_BASE_DELAY * (1 shl (retryCount - 1))
                        delay(reconnectDelay + jitter)
                        retryCount = (retryCount + 1).coerceAtMost(RECONNECT_MAX_ATTEMPTS)
                    }
                }

                logger.error { "[PubSub $tag] connection failed after $RECONNECT_MAX_ATTEMPTS retries" }
                session = null
            }

        return remainingTopics.toSet()
    }

    fun listen(newTopics: Set<PubSubTopic>): Set<PubSubTopic> {
        if (!canAcceptTopics) {
            return newTopics
        }

        val (possibleTopics, remainingTopics) = (topics + newTopics).splitAt(MAX_TOPICS)
        val needsListen = (possibleTopics - topics)
        topics.addAll(needsListen)

        if (needsListen.isNotEmpty()) {
            logger.debug { "[PubSub $tag] listening to ${needsListen.size} new topics" }
        }
        val currentSession = session
        needsListen
            .toRequestMessages()
            .forEach { message ->
                scope.launch { runCatching { currentSession?.send(Frame.Text(message)) } }
            }

        return remainingTopics.toSet()
    }

    fun unlistenByChannel(channel: UserName) {
        val toUnlisten = topics.filter { it.channelName == channel }
        unlisten(toUnlisten.toSet())
    }

    fun close() {
        val currentSession = session
        session = null
        connectionJob?.cancel()
        scope.launch {
            runCatching {
                currentSession?.close()
                currentSession?.cancel()
            }
        }
    }

    fun reconnect() {
        logger.info { "[PubSub $tag] reconnecting" }
        close()
        connect(topics)
    }

    fun reconnectIfNecessary() {
        if (connected || connectionJob?.isActive == true) return
        logger.info { "[PubSub $tag] connection lost, reconnecting" }
        reconnect()
    }

    private fun unlisten(toUnlisten: Set<PubSubTopic>) {
        val foundTopics = topics.filter { it in toUnlisten }.toSet()
        topics.removeAll(foundTopics)

        if (foundTopics.isNotEmpty()) {
            logger.debug { "[PubSub $tag] unlistening from ${foundTopics.size} topics" }
        }
        val currentSession = session
        foundTopics
            .toRequestMessages(type = "UNLISTEN")
            .forEach { message ->
                scope.launch { runCatching { currentSession?.send(Frame.Text(message)) } }
            }
    }

    private fun randomJitter() = Random.nextLong(range = 0L..MAX_JITTER).milliseconds

    private fun setupPingInterval() = scope.timer(interval = PING_INTERVAL - randomJitter()) {
        val currentSession = session
        if (awaitingPong || currentSession?.isActive != true) {
            cancel()
            reconnect()
            return@timer
        }

        if (connected) {
            awaitingPong = true
            runCatching { currentSession.send(Frame.Text(PING_PAYLOAD)) }
        }
    }

    /**
     * Handles a PubSub message. Returns true if the server requested a reconnect.
     */
    @Suppress("ReturnCount")
    private fun handleMessage(text: String): Boolean {
        val json = JSONObject(text)
        val type = json.optString("type").ifBlank { return false }
        when (type) {
            "PONG" -> {
                awaitingPong = false
            }

            "RECONNECT" -> {
                logger.info { "[PubSub $tag] server requested reconnect" }
                return true
            }

            "RESPONSE" -> {
                val error = json.optString("error")
                if (error.isNotBlank()) {
                    logger.warn { "[PubSub $tag] RESPONSE error: $error" }
                }
            }

            "MESSAGE" -> {
                val data = json.optJSONObject("data") ?: return false
                val topic = data.optString("topic").ifBlank { return false }
                val message = data.optString("message").ifBlank { return false }
                val messageObject = JSONObject(message)
                val messageTopic = messageObject.optString("type")
                val match = topics.find { topic == it.topic } ?: return false
                val pubSubMessage =
                    when (match) {
                        is PubSubTopic.PointRedemptions -> {
                            if (messageTopic !in POINT_REDEMPTION_TOPICS) {
                                return false
                            }

                            val parsedMessage = jsonFormat.decodeOrNull<PubSubDataMessage<PointRedemption>>(message) ?: return false
                            PubSubMessage.PointRedemption(
                                timestamp = parsedMessage.data.timestamp,
                                channelName = match.channelName,
                                channelId = match.channelId,
                                data = parsedMessage.data.redemption,
                            )
                        }

                        is PubSubTopic.ModeratorActions -> {
                            when (messageTopic) {
                                "moderator_added" -> {
                                    val parsedMessage = jsonFormat.decodeOrNull<PubSubDataMessage<ModeratorAddedData>>(message) ?: return false
                                    val timestamp = Clock.System.now()
                                    PubSubMessage.ModeratorAction(
                                        timestamp = timestamp,
                                        channelId = parsedMessage.data.channelId,
                                        data =
                                            ModerationActionData(
                                                args = null,
                                                targetUserId = parsedMessage.data.targetUserId,
                                                targetUserName = parsedMessage.data.targetUserName,
                                                moderationAction = parsedMessage.data.moderationAction,
                                                creatorUserId = parsedMessage.data.creatorUserId,
                                                creator = parsedMessage.data.creator,
                                                createdAt = timestamp.toString(),
                                                msgId = null,
                                            ),
                                    )
                                }

                                "moderation_action" -> {
                                    val parsedMessage = jsonFormat.decodeOrNull<PubSubDataMessage<ModerationActionData>>(message) ?: return false
                                    if (parsedMessage.data.moderationAction == ModerationActionType.Mod) {
                                        return false
                                    }
                                    val timestamp =
                                        when {
                                            parsedMessage.data.createdAt.isEmpty() -> Clock.System.now()
                                            else -> Instant.parse(parsedMessage.data.createdAt)
                                        }
                                    PubSubMessage.ModeratorAction(
                                        timestamp = timestamp,
                                        channelId = topic.substringAfterLast('.').toUserId(),
                                        data =
                                            parsedMessage.data.copy(
                                                msgId = parsedMessage.data.msgId?.ifBlank { null },
                                                creator = parsedMessage.data.creator?.ifBlank { null },
                                                creatorUserId = parsedMessage.data.creatorUserId?.ifBlank { null },
                                                targetUserId = parsedMessage.data.targetUserId?.ifBlank { null },
                                                targetUserName = parsedMessage.data.targetUserName?.ifBlank { null },
                                            ),
                                    )
                                }

                                else -> {
                                    return false
                                }
                            }
                        }

                        is PubSubTopic.PinnedChatUpdates -> {
                            // The payload body is undocumented, the pin content is fetched via Helix instead
                            when (messageTopic) {
                                "pin-message", "update-message" -> {
                                    PubSubMessage.PinnedChatUpdate(channelName = match.channelName, removed = false)
                                }

                                "unpin-message" -> {
                                    PubSubMessage.PinnedChatUpdate(channelName = match.channelName, removed = true)
                                }

                                else -> {
                                    return false
                                }
                            }
                        }
                    }
                receiveChannel.trySend(PubSubEvent.Message(pubSubMessage))
            }
        }
        return false
    }

    private fun <T> Collection<T>.splitAt(n: Int): Pair<Collection<T>, Collection<T>> = take(n) to drop(n)

    private fun Collection<PubSubTopic>.toRequestMessages(type: String = "LISTEN"): List<String> {
        val (authedTopics, unauthedTopics) = partition { it.requiresAuth }
        return listOf(
            authedTopics.toRequestMessage(type),
            unauthedTopics.toRequestMessage(type, withAuth = false),
        )
    }

    private fun Collection<PubSubTopic>.toRequestMessage(
        type: String = "LISTEN",
        withAuth: Boolean = true,
    ): String {
        val nonce = UUID.randomUUID().toString()
        val message =
            buildJsonObject {
                put("type", type)
                put("nonce", nonce)
                putJsonObject("data") {
                    putJsonArray("topics") {
                        forEach { add(it.topic) }
                    }
                    if (withAuth) {
                        put("auth_token", currentOAuth)
                    }
                }
            }

        return message.toString()
    }

    companion object {
        const val MAX_TOPICS = 50
        const val MAX_CONNECTIONS = 10
        private const val MAX_JITTER = 250L
        private val RECONNECT_BASE_DELAY = 1.seconds
        private const val RECONNECT_MAX_ATTEMPTS = 6
        private val PING_INTERVAL = 5.minutes
        private const val PING_PAYLOAD = "{\"type\":\"PING\"}"
        private const val PUBSUB_URL = "wss://pubsub-edge.twitch.tv"
        private val POINT_REDEMPTION_TOPICS = setOf("reward-redeemed", "automatic-reward-redeemed")
    }
}
