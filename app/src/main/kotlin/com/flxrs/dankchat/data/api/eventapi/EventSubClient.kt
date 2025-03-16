package com.flxrs.dankchat.data.api.eventapi

import android.util.Log
import com.flxrs.dankchat.data.api.eventapi.dto.messages.EventSubMessageDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.KeepAliveMessageDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.NotificationMessageDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.ReconnectMessageDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.RevocationMessageDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.WelcomeMessageDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.ChannelModerateDto
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.di.DispatchersProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.util.collections.ConcurrentSet
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.koin.core.annotation.Single
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class)
@Single
class EventSubClient(
    private val helixApiClient: HelixApiClient,
    private val json: Json,
    httpClient: HttpClient,
    dispatchersProvider: DispatchersProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.io)
    private var session: DefaultClientWebSocketSession? = null
    private var previousSession: DefaultClientWebSocketSession? = null
    private val wantedSubscriptions = ConcurrentSet<EventSubTopic>()
    private val subscriptions = MutableStateFlow<Set<SubscribedTopic>>(emptySet())
    private val subscriptionMutex = Mutex()
    private val _state = MutableStateFlow<EventSubClientState>(EventSubClientState.Disconnected)

    private val eventsChannel = Channel<EventSubMessage>(Channel.UNLIMITED)

    private val client = httpClient.config {
        install(WebSockets)
    }

    val connected get() = session?.isActive == true && session?.incoming?.isClosedForReceive == false
    val state = _state.asStateFlow()
    val topics = subscriptions.asStateFlow()
    val events = eventsChannel.receiveAsFlow().shareIn(scope = scope, started = SharingStarted.Eagerly)

    fun connect(url: String = DEFAULT_URL, twitchReconnect: Boolean = false) {
        Log.i(TAG, "[EventSub] starting connection, twitchReconnect=$twitchReconnect")
        emitSystemMessage(message = "[EventSub] connecting, twitchReconnect=$twitchReconnect")

        if (!twitchReconnect) {
            _state.update { EventSubClientState.Connecting }
        }

        scope.launch {
            var sessionId: String? = null
            var retryCount = 0
            while (retryCount < RECONNECT_MAX_ATTEMPTS) {
                try {
                    client.webSocket(url) {
                        session = this
                        while (isActive) {
                            val result = incoming.receiveCatching()
                            val raw = when (val element = result.getOrNull()) {
                                null -> {
                                    val cause = result.exceptionOrNull()
                                    if (cause == null) {
                                        // websocket likely received a close frame, no need to reconnect
                                        return@webSocket
                                    }

                                    // rethrow to trigger reconnect logic
                                    throw cause
                                }

                                else -> (element as? Frame.Text)?.readText() ?: continue
                            }

                            //Log.v(TAG, "[EventSub] Received raw message: $raw")

                            val jsonObject = json
                                .parseToJsonElement(raw)
                                .fixDiscriminators()

                            val message = runCatching { json.decodeFromJsonElement<EventSubMessageDto>(jsonObject) }
                                .getOrElse {
                                    Log.e(TAG, "[EventSub] failed to parse message: $it")
                                    emitSystemMessage(message = "[EventSub] failed to parse message: $it")
                                    continue
                                }

                            when (message) {
                                is WelcomeMessageDto      -> {
                                    retryCount = 0
                                    sessionId = message.payload.session.id
                                    Log.i(TAG, "[EventSub]($sessionId) received welcome message, status=${message.payload.session.status}")
                                    emitSystemMessage(message = "[EventSub]($sessionId) received welcome message, status=${message.payload.session.status}")
                                    _state.update { EventSubClientState.Connected(message.payload.session.id) }

                                    if (twitchReconnect) {
                                        scope.launch {
                                            previousSession?.closeAndCancel()
                                            previousSession = null
                                        }

                                        continue
                                    }

                                    scope.launch {
                                        wantedSubscriptions.forEach {
                                            subscribe(it)
                                        }
                                    }
                                }

                                is ReconnectMessageDto    -> handleReconnect(message)
                                is RevocationMessageDto   -> handleRevocation(message)
                                is NotificationMessageDto -> handleNotification(message)
                                is KeepAliveMessageDto    -> Unit
                            }
                        }
                    }

                    Log.i(TAG, "[EventSub]($sessionId) connection closed")
                    emitSystemMessage(message = "[EventSub]($sessionId) connection closed")

                    shouldDiscardSession(sessionId)
                    return@launch

                } catch (t: Throwable) {
                    Log.e(TAG, "[EventSub]($sessionId) connection failed: $t")
                    emitSystemMessage(message = "[EventSub]($sessionId) connection failed: $t")
                    if (shouldDiscardSession(sessionId)) {
                        return@launch
                    }

                    val jitter = Random.nextLong(0L..MAX_JITTER)
                    val reconnectDelay = RECONNECT_BASE_DELAY * (1 shl (retryCount - 1))
                    delay(reconnectDelay + jitter)
                    retryCount = (retryCount + 1).coerceAtMost(RECONNECT_MAX_ATTEMPTS)
                    Log.i(TAG, "[EventSub] attempting to reconnect #$retryCount..")
                    emitSystemMessage(message = "[EventSub] attempting to reconnect #$retryCount..")
                }
            }

            Log.e(TAG, "[EventSub] connection failed after $retryCount retries, cleaning up..")
            emitSystemMessage(message = "[EventSub] connection failed after $retryCount retries, cleaning up..")
            _state.update { EventSubClientState.Failed }
            subscriptions.update { emptySet() }
            session = null
        }
    }

    suspend fun subscribe(topic: EventSubTopic) = subscriptionMutex.withLock {
        wantedSubscriptions += topic
        if (subscriptions.value.any { it.topic == topic }) {
            // already subscribed, nothing to do
            return@withLock
        }

        // check state, if we are not connected, we need to start a connection
        val current = state.value
        if (current is EventSubClientState.Disconnected || current is EventSubClientState.Failed) {
            Log.d(TAG, "[EventSub] is not connected, connecting")
            connect()
        }

        val connectedState = withTimeoutOrNull(SUBSCRIPTION_TIMEOUT) {
            state.first { it is EventSubClientState.Connected } as EventSubClientState.Connected
        } ?: return@withLock

        val request = topic.createRequest(connectedState.sessionId)
        val response = helixApiClient.postEventSubSubscription(request)
            .getOrElse {
                // TODO: handle errors, maybe retry?
                Log.e(TAG, "[EventSub] failed to subscribe: $it")
                emitSystemMessage(message = "[EventSub] failed to subscribe: $it")
                return@withLock
            }

        val subscription = response.data.firstOrNull()?.id
        if (subscription == null) {
            Log.e(TAG, "[EventSub] subscription response did not include subscription id: $response")
            return@withLock
        }

        Log.d(TAG, "[EventSub] subscribed to $topic")
        emitSystemMessage(message = "[EventSub] subscribed to ${topic.shortFormatted()}")
        subscriptions.update { it + SubscribedTopic(subscription, topic) }
    }

    suspend fun unsubscribe(topic: SubscribedTopic) {
        wantedSubscriptions -= topic.topic
        helixApiClient.deleteEventSubSubscription(topic.id)
            .getOrElse {
                // TODO: handle errors, maybe retry?
                Log.e(TAG, "[EventSub] failed to unsubscribe: $it")
                emitSystemMessage(message = "[EventSub] failed to unsubscribe: $it")
                return@getOrElse
            }

        Log.d(TAG, "[EventSub] unsubscribed from $topic")
        emitSystemMessage(message = "[EventSub] unsubscribed from ${topic.topic.shortFormatted()}")
        subscriptions.update { it - topic }
    }

    suspend fun closeAndClearTopics() {
        session?.closeAndCancel()
        session = null
        wantedSubscriptions.clear()
    }

    fun reconnect() {
        scope.launch {
            session?.closeAndCancel()
            connect()
        }
    }

    fun reconnectIfNecessary() {
        if (session?.isActive == true && session?.incoming?.isClosedForReceive == false) {
            return
        }

        reconnect()
    }

    private fun handleNotification(message: NotificationMessageDto) {
        Log.d(TAG, "[EventSub] received notification message: $message")
        val event = message.payload.event
        val message = when (event) {
            is ChannelModerateDto -> ModerationAction(
                id = message.metadata.messageId,
                timestamp = message.metadata.messageTimestamp,
                channelName = event.broadcasterUserLogin,
                data = event,
            )
        }
        eventsChannel.trySend(message)
    }

    private fun handleRevocation(message: RevocationMessageDto) {
        Log.i(TAG, "[EventSub] received revocation message for subscription: ${message.payload.subscription}")
        emitSystemMessage(message = "[EventSub] received revocation message for subscription: ${message.payload.subscription}")
        subscriptions.update { it.filterTo(mutableSetOf()) { it.id == message.payload.subscription.id } }
    }

    private fun DefaultClientWebSocketSession.handleReconnect(message: ReconnectMessageDto) {
        Log.i(TAG, "[EventSub] received request to reconnect: ${message.payload.session.reconnectUrl}")
        emitSystemMessage(message = "[EventSub] received request to reconnect")
        val url = message.payload.session.reconnectUrl?.replaceFirst("ws://", "wss://")
        when (url) {
            null -> reconnect()
            else -> {
                previousSession = this
                connect(url = url, twitchReconnect = true)
            }
        }
    }

    private fun emitSystemMessage(message: String) {
        val systemMessage = SystemMessage(message = message)
        eventsChannel.trySend(systemMessage)
    }

    private suspend fun DefaultClientWebSocketSession.closeAndCancel() {
        close()
        cancel()
    }

    private fun shouldDiscardSession(sessionId: String?): Boolean {
        _state.update { current ->
            when (current) {
                // this session got closed but we are already connected to a new one, don't update the state
                is EventSubClientState.Connected if sessionId != current.sessionId -> {
                    Log.d(TAG, "[EventSub]($sessionId) Discarding session as we are already connected to a new one (${current.sessionId})")
                    emitSystemMessage(message = "[EventSub]($sessionId) Discarding session as we are already connected to a new one (${current.sessionId})")
                    return true
                }

                else                                                               -> {
                    subscriptions.update { emptySet() }
                    EventSubClientState.Disconnected
                }
            }
        }
        return false
    }

    private fun JsonElement.fixDiscriminators(): JsonElement {
        if (this !is JsonObject) return this
        val root = this.toMutableMap()
        val metadata = this["metadata"]
        if (metadata !is JsonObject) return this

        // move message type discriminator to top level so it can be used by the deserializer
        val discriminator = metadata["message_type"]
        if (discriminator !is JsonPrimitive || !discriminator.isString) return this
        root["message_type"] = discriminator

        val payload = this["payload"]
        if (payload !is JsonObject) return JsonObject(root)

        // if message type is notification, move event type from subscription object to event object so it can be used by the deserializer
        if (discriminator.content == "notification") {
            val subscription = payload["subscription"]
            val event = payload["event"]
            if (subscription is JsonObject && event is JsonObject) {
                val type = subscription["type"]
                if (type is JsonPrimitive && type.isString) {
                    val eventMap = event.toMutableMap()
                    val payloadMap = payload.toMutableMap()
                    eventMap["type"] = type
                    payloadMap["event"] = JsonObject(eventMap)
                    root["payload"] = JsonObject(payloadMap)
                    return JsonObject(root)
                }
            }
        }

        return JsonObject(root)
    }

    private companion object {
        const val DEFAULT_URL = "wss://eventsub.wss.twitch.tv/ws?keepalive_timeout_seconds=30"
        const val MAX_JITTER = 250L
        const val RECONNECT_BASE_DELAY = 1_000L
        const val RECONNECT_MAX_ATTEMPTS = 6
        val SUBSCRIPTION_TIMEOUT = 5.seconds
        val TAG = EventSubClient::class.simpleName
    }
}
