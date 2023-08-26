package com.flxrs.dankchat.data.twitch.pubsub

import android.util.Log
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.ifBlank
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.twitch.pubsub.dto.moderation.ModerationActionData
import com.flxrs.dankchat.data.twitch.pubsub.dto.moderation.ModerationActionType
import com.flxrs.dankchat.data.twitch.pubsub.dto.moderation.ModeratorAddedData
import com.flxrs.dankchat.data.twitch.pubsub.dto.redemption.PointRedemption
import com.flxrs.dankchat.data.twitch.pubsub.dto.PubSubDataMessage
import com.flxrs.dankchat.data.twitch.pubsub.dto.PubSubDataObjectMessage
import com.flxrs.dankchat.data.twitch.pubsub.dto.whisper.WhisperData
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import com.flxrs.dankchat.utils.extensions.timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PubSubConnection(
    val tag: String,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val oAuth: String,
    private val jsonFormat: Json,
) {
    private var socket: WebSocket? = null
    private val request = Request.Builder().url("wss://pubsub-edge.twitch.tv").build()
    private val receiveChannel = Channel<PubSubEvent>(capacity = Channel.BUFFERED)

    private var connecting = false
    private var awaitingPong = false
    private var reconnectAttempts = 1
    private val currentReconnectDelay: Duration
        get() {
            val jitter = randomJitter()
            val reconnectDelay = RECONNECT_BASE_DELAY * (1 shl (reconnectAttempts - 1))
            reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(RECONNECT_MAX_ATTEMPTS)

            return reconnectDelay + jitter
        }

    private val topics = mutableSetOf<PubSubTopic>()
    private lateinit var currentOAuth: String
    private val canAcceptTopics: Boolean
        get() = connected && topics.size < MAX_TOPICS

    var connected = false
        private set
    val hasTopics: Boolean
        get() = topics.isNotEmpty()
    val hasWhisperTopic: Boolean
        get() = topics.any { it.topic.startsWith("whispers.") }

    fun hasModeratorTopic(userId: UserId, channelId: UserId): Boolean {
        return topics.any { it.topic.startsWith("chat_moderator_actions.$userId.$channelId") }
    }

    val events = receiveChannel.receiveAsFlow().distinctUntilChanged { old, new ->
        (old.isDisconnected && new.isDisconnected) || old == new
    }

    fun connect(initialTopics: Set<PubSubTopic>): Set<PubSubTopic> {
        if (connected || connecting) {
            return initialTopics
        }

        currentOAuth = oAuth
        awaitingPong = false
        connecting = true

        val (possibleTopics, remainingTopics) = initialTopics.splitAt(MAX_TOPICS)
        socket = client.newWebSocket(request, PubSubWebSocketListener(possibleTopics))
        topics.clear()
        topics.addAll(possibleTopics)

        return remainingTopics.toSet()
    }

    fun listen(newTopics: Set<PubSubTopic>): Set<PubSubTopic> {
        if (!canAcceptTopics) {
            return newTopics
        }

        val (possibleTopics, remainingTopics) = (topics + newTopics).splitAt(MAX_TOPICS)
        val needsListen = (possibleTopics - topics)
        topics.addAll(needsListen)

        val listenMessage = needsListen.toRequestMessage()
        socket?.send(listenMessage)

        return remainingTopics.toSet()
    }

    fun unlistenByChannel(channel: UserName) {
        val toUnlisten = topics
            .filterIsInstance<PubSubTopic.PointRedemptions>()
            .filter { it.channelName == channel }
        unlisten(toUnlisten.toSet())
    }

    fun close() {
        connected = false
        socket?.close(1000, null) ?: socket?.cancel()
        socket = null
    }

    fun reconnect() {
        reconnectAttempts = 1
        attemptReconnect()
    }

    fun reconnectIfNecessary() {
        if (connected || connecting) return
        reconnect()
    }

    private fun unlisten(toUnlisten: Set<PubSubTopic>) {
        val foundTopics = topics.filter { it in toUnlisten }.toSet()
        topics.removeAll(foundTopics)

        val message = foundTopics.toRequestMessage(type = "UNLISTEN")
        socket?.send(message)
    }

    private fun attemptReconnect() {
        scope.launch {
            delay(currentReconnectDelay)
            close()
            connect(topics)
        }
    }

    private fun randomJitter() = Random.nextLong(range = 0L..MAX_JITTER).milliseconds

    private fun setupPingInterval() = scope.timer(interval = PING_INTERVAL - randomJitter()) {
        val webSocket = socket
        if (awaitingPong || webSocket == null) {
            cancel()
            reconnect()
            return@timer
        }

        if (connected) {
            awaitingPong = true
            webSocket.send(PING_PAYLOAD)
        }
    }

    private inner class PubSubWebSocketListener(private val initialTopics: Collection<PubSubTopic>) : WebSocketListener() {
        private var pingJob: Job? = null

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected = false
            pingJob?.cancel()
            receiveChannel.trySend(PubSubEvent.Closed)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "[PubSub $tag] connection failed: $t")
            Log.e(TAG, "[PubSub $tag] attempting to reconnect #${reconnectAttempts}..")
            connected = false
            connecting = false
            pingJob?.cancel()
            receiveChannel.trySend(PubSubEvent.Closed)

            attemptReconnect()
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            connecting = false
            reconnectAttempts = 1
            receiveChannel.trySend(PubSubEvent.Connected)
            Log.i(TAG, "[PubSub $tag] connected")

            val listenMessage = initialTopics.toRequestMessage()
            webSocket.send(listenMessage)
            pingJob = setupPingInterval()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = JSONObject(text)
            val type = json.optString("type").ifBlank { return }
            when (type) {
                "PONG"      -> awaitingPong = false
                "RECONNECT" -> reconnect()
                "RESPONSE"  -> {}
                "MESSAGE"   -> {
                    val data = json.optJSONObject("data") ?: return
                    val topic = data.optString("topic").ifBlank { return }
                    val message = data.optString("message").ifBlank { return }
                    val messageObject = JSONObject(message)
                    val messageTopic = messageObject.optString("type")
                    val match = topics.find { topic == it.topic } ?: return
                    val pubSubMessage = when (match) {
                        is PubSubTopic.Whispers         -> {
                            if (messageTopic !in listOf("whisper_sent", "whisper_received")) {
                                return
                            }

                            val parsedMessage = jsonFormat.decodeOrNull<PubSubDataObjectMessage<WhisperData>>(message) ?: return
                            PubSubMessage.Whisper(parsedMessage.data)
                        }

                        is PubSubTopic.PointRedemptions -> {
                            if (messageTopic != "reward-redeemed") {
                                return
                            }

                            val parsedMessage = jsonFormat.decodeOrNull<PubSubDataMessage<PointRedemption>>(message) ?: return
                            PubSubMessage.PointRedemption(
                                timestamp = Instant.parse(parsedMessage.data.timestamp),
                                channelName = match.channelName,
                                channelId = match.channelId,
                                data = parsedMessage.data.redemption
                            )
                        }

                        is PubSubTopic.ModeratorActions -> {
                            when (messageTopic) {
                                "moderator_added"   -> {
                                    val parsedMessage = jsonFormat.decodeOrNull<PubSubDataMessage<ModeratorAddedData>>(message) ?: return
                                    val timestamp = Instant.now()
                                    PubSubMessage.ModeratorAction(
                                        timestamp = timestamp,
                                        channelId = parsedMessage.data.channelId,
                                        data = ModerationActionData(
                                            args = null,
                                            targetUserId = parsedMessage.data.targetUserId,
                                            targetUserName = parsedMessage.data.targetUserName,
                                            moderationAction = parsedMessage.data.moderationAction,
                                            creatorUserId = parsedMessage.data.creatorUserId,
                                            creator = parsedMessage.data.creator,
                                            createdAt = timestamp.toString(),
                                            msgId = null
                                        )
                                    )
                                }

                                "moderation_action" -> {
                                    val parsedMessage = jsonFormat.decodeOrNull<PubSubDataMessage<ModerationActionData>>(message) ?: return
                                    if (parsedMessage.data.moderationAction == ModerationActionType.Mod) {
                                        return
                                    }
                                    val timestamp = when {
                                        parsedMessage.data.createdAt.isEmpty() -> Instant.now()
                                        else                                   -> Instant.parse(parsedMessage.data.createdAt)
                                    }
                                    PubSubMessage.ModeratorAction(
                                        timestamp = timestamp,
                                        channelId = topic.substringAfterLast('.').toUserId(),
                                        data = parsedMessage.data.copy(
                                            msgId = parsedMessage.data.msgId?.ifBlank { null },
                                            creator = parsedMessage.data.creator?.ifBlank { null },
                                            creatorUserId = parsedMessage.data.creatorUserId?.ifBlank { null },
                                            targetUserId = parsedMessage.data.targetUserId?.ifBlank { null },
                                            targetUserName = parsedMessage.data.targetUserName?.ifBlank { null },
                                        )
                                    )
                                }

                                else                -> return
                            }
                        }
                    }
                    receiveChannel.trySend(PubSubEvent.Message(pubSubMessage))
                }
            }

        }
    }

    private fun <T> Collection<T>.splitAt(n: Int): Pair<Collection<T>, Collection<T>> {
        return take(n) to drop(n)
    }

    private fun Collection<PubSubTopic>.toRequestMessage(type: String = "LISTEN"): String {
        val message = JSONObject().apply {
            put("type", type)
            put("nonce", UUID.randomUUID().toString())
            val data = JSONObject().let { data ->
                val mappedTopics = map { it.topic }
                data.put("topics", JSONArray(mappedTopics))
                data.put("auth_token", oAuth)
            }
            put("data", data)
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
        private val TAG = PubSubConnection::class.java.simpleName
    }
}
