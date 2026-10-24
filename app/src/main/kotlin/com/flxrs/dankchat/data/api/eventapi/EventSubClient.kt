package com.flxrs.dankchat.data.api.eventapi

import com.flxrs.dankchat.data.api.eventapi.dto.messages.EventSubMessageDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.KeepAliveMessageDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.NotificationMessageDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.ReconnectMessageDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.RevocationMessageDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.WelcomeMessageDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.AutomodMessageHoldDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.AutomodMessageUpdateDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.ChannelChatUserMessageHoldDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.ChannelChatUserMessageUpdateDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.ChannelModerateDto
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.HelixApiException
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.ForegroundServiceState
import com.flxrs.dankchat.utils.webSocketCoroutineExceptionHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpStatusCode
import io.ktor.util.collections.ConcurrentSet
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.IOException
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger("EventSubClient")

@OptIn(DelicateCoroutinesApi::class)
class EventSubClient(
    private val helixApiClient: HelixApiClient,
    private val json: Json,
    private val foregroundServiceState: ForegroundServiceState,
    httpClient: HttpClient,
    dispatchersProvider: DispatchersProvider,
    private val defaultUrl: String = DEFAULT_URL,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.io)
    private var session: DefaultClientWebSocketSession? = null
    private var previousSession: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private var previousConnectionJob: Job? = null
    private val wantedSubscriptions = ConcurrentSet<EventSubTopic>()
    private val subscriptions = MutableStateFlow<Set<SubscribedTopic>>(emptySet())
    private val subscriptionMutex = Mutex()
    private val _state = MutableStateFlow<EventSubClientState>(EventSubClientState.Disconnected)

    private val eventsChannel = Channel<EventSubMessage>(Channel.UNLIMITED)

    // EventSub delivers notifications at least once, duplicates have to be detected via metadata.message_id
    private val seenNotificationIds = mutableSetOf<String>()

    private val client =
        httpClient.config {
            install(WebSockets)
        }

    val connected get() = session?.isActive == true && session?.incoming?.isClosedForReceive == false
    val state = _state.asStateFlow()
    val topics = subscriptions.asStateFlow()
    val events = eventsChannel.receiveAsFlow().shareIn(scope = scope, started = SharingStarted.Eagerly)

    @Synchronized
    fun connect(
        url: String = defaultUrl,
        twitchReconnect: Boolean = false,
    ) {
        logger.info { "[EventSub] starting connection, twitchReconnect=$twitchReconnect" }
        emitSystemMessage(message = "[EventSub] connecting, twitchReconnect=$twitchReconnect")

        // Only one connection loop may run, a leaked loop keeps its own session and subscriptions alive and duplicates every notification
        previousConnectionJob?.cancel()
        when {
            twitchReconnect -> {
                previousConnectionJob = connectionJob
            }

            else -> {
                previousConnectionJob = null
                connectionJob?.cancel()
                subscriptions.update { emptySet() }
                _state.update { EventSubClientState.Connecting }
            }
        }

        connectionJob = scope.launch(webSocketCoroutineExceptionHandler("EventSub")) {
            val loopJob = coroutineContext.job
            var currentUrl = url
            var isMigration = twitchReconnect
            var sessionId: String? = null
            var keepAliveTimeout = DEFAULT_KEEPALIVE_TIMEOUT
            // The counter saturates at the max, so like IRC this keeps retrying with a capped delay
            var retryCount = 1
            while (retryCount <= RECONNECT_MAX_ATTEMPTS) {
                try {
                    client.webSocket(currentUrl) {
                        session = this
                        while (isActive) {
                            // Twitch sends a keepalive when there is nothing else to deliver, silence means the connection is dead
                            val result =
                                withTimeoutOrNull(keepAliveTimeout) { incoming.receiveCatching() }
                                    ?: throw IOException("no message received within $keepAliveTimeout")
                            val raw =
                                when (val element = result.getOrNull()) {
                                    null -> {
                                        // a close frame ends the session, the loop below reconnects
                                        val cause = result.exceptionOrNull() ?: return@webSocket

                                        // rethrow to trigger reconnect logic
                                        throw cause
                                    }

                                    else -> {
                                        (element as? Frame.Text)?.readText() ?: continue
                                    }
                                }

                            // logger.trace { "[EventSub] Received raw message: $raw" }

                            val jsonObject =
                                json
                                    .parseToJsonElement(raw)
                                    .fixDiscriminators()

                            val message =
                                runCatching { json.decodeFromJsonElement<EventSubMessageDto>(jsonObject) }
                                    .getOrElse {
                                        logger.error { "[EventSub] failed to parse message: $it" }
                                        logger.error { "[EventSub] raw JSON: $jsonObject" }
                                        emitSystemMessage(message = "[EventSub] failed to parse message: $it")
                                        continue
                                    }

                            when (message) {
                                is WelcomeMessageDto -> {
                                    retryCount = 1
                                    sessionId = message.payload.session.id
                                    message.payload.session.keepAliveSeconds
                                        ?.let { keepAliveTimeout = (it * 2).seconds }
                                    logger.info { "[EventSub]($sessionId) received welcome message, status=${message.payload.session.status}" }
                                    emitSystemMessage(message = "[EventSub]($sessionId) received welcome message, status=${message.payload.session.status}")
                                    _state.update { EventSubClientState.Connected(message.payload.session.id) }

                                    if (isMigration) {
                                        closePreviousConnection()
                                        continue
                                    }

                                    scope.launch {
                                        wantedSubscriptions.forEach {
                                            subscribe(it)
                                        }
                                    }
                                }

                                is ReconnectMessageDto -> {
                                    handleReconnect(message)
                                }

                                is RevocationMessageDto -> {
                                    handleRevocation(message)
                                }

                                is NotificationMessageDto -> {
                                    handleNotification(message)
                                }

                                is KeepAliveMessageDto -> Unit
                            }
                        }
                    }

                    ensureActive()
                    logger.info { "[EventSub]($sessionId) connection closed" }
                    emitSystemMessage(message = "[EventSub]($sessionId) connection closed")
                } catch (t: Throwable) {
                    ensureActive()
                    logger.error { "[EventSub]($sessionId) connection failed: $t" }
                    emitSystemMessage(message = "[EventSub]($sessionId) connection failed: $t")
                }

                // A replaced connection must not touch the state of its successor
                if (!onConnectionLost(loopJob)) {
                    logger.debug { "[EventSub]($sessionId) discarding replaced connection" }
                    return@launch
                }

                when {
                    !foregroundServiceState.active.value -> {
                        logger.info { "[EventSub] foreground service inactive, not retrying" }
                        emitSystemMessage(message = "[EventSub] foreground service inactive, not retrying")
                        return@launch
                    }

                    wantedSubscriptions.isEmpty() -> {
                        logger.info { "[EventSub] no topics wanted, not retrying" }
                        return@launch
                    }
                }

                // The reconnect url is single use, a lost connection starts over with a fresh session and resubscribes
                currentUrl = defaultUrl
                isMigration = false
                val jitter = Random.nextLong(0L..MAX_JITTER)
                val reconnectDelay = RECONNECT_BASE_DELAY * (1 shl (retryCount - 1))
                delay(reconnectDelay + jitter)
                retryCount = (retryCount + 1).coerceAtMost(RECONNECT_MAX_ATTEMPTS)
                logger.info { "[EventSub] attempting to reconnect #$retryCount.." }
                emitSystemMessage(message = "[EventSub] attempting to reconnect #$retryCount..")
            }

            logger.error { "[EventSub] connection failed after $retryCount retries, cleaning up.." }
            emitSystemMessage(message = "[EventSub] connection failed after $retryCount retries, cleaning up..")
            _state.update { EventSubClientState.Failed }
            subscriptions.update { emptySet() }
            session = null
        }
    }

    suspend fun subscribe(
        topic: EventSubTopic,
        attempt: Int = 1,
    ): Unit = subscriptionMutex.withLock {
        when {
            attempt == 1 -> wantedSubscriptions += topic

            // topic was unsubscribed while a retry was pending
            topic !in wantedSubscriptions -> return@withLock
        }
        if (subscriptions.value.any { it.topic == topic }) {
            // already subscribed, nothing to do
            return@withLock
        }

        // check state, if we are not connected, we need to start a connection
        val current = state.value
        if (current is EventSubClientState.Disconnected || current is EventSubClientState.Failed) {
            logger.debug { "[EventSub] is not connected, connecting" }
            connect()
        }

        val connectedState =
            withTimeoutOrNull(SUBSCRIPTION_TIMEOUT) {
                state.filterIsInstance<EventSubClientState.Connected>().first()
            } ?: return@withLock

        val request = topic.createRequest(connectedState.sessionId)
        val response =
            helixApiClient
                .postEventSubSubscription(request)
                .getOrElse {
                    logger.error { "[EventSub] failed to subscribe: $it" }
                    emitSystemMessage(message = "[EventSub] failed to subscribe: $it")
                    if (it.isTransient() && attempt < SUBSCRIBE_MAX_ATTEMPTS) {
                        scope.launch {
                            delay(SUBSCRIBE_RETRY_DELAY * attempt)
                            logger.info { "[EventSub] retrying subscription to ${topic.shortFormatted()}, attempt ${attempt + 1}" }
                            subscribe(topic, attempt + 1)
                        }
                    }
                    return@withLock
                }

        val subscription = response.data.firstOrNull()?.id
        if (subscription == null) {
            logger.error { "[EventSub] subscription response did not include subscription id: $response" }
            return@withLock
        }

        logger.debug { "[EventSub] subscribed to $topic" }
        emitSystemMessage(message = "[EventSub] subscribed to ${topic.shortFormatted()}")
        subscriptions.update { it + SubscribedTopic(subscription, topic) }
    }

    suspend fun unsubscribe(topic: SubscribedTopic) {
        wantedSubscriptions -= topic.topic
        helixApiClient
            .deleteEventSubSubscription(topic.id)
            .getOrElse {
                // TODO: handle errors, maybe retry?
                logger.error { "[EventSub] failed to unsubscribe: $it" }
                emitSystemMessage(message = "[EventSub] failed to unsubscribe: $it")
                return@getOrElse
            }

        logger.debug { "[EventSub] unsubscribed from $topic" }
        emitSystemMessage(message = "[EventSub] unsubscribed from ${topic.topic.shortFormatted()}")
        subscriptions.update { it - topic }
    }

    suspend fun closeAndClearTopics() {
        session?.closeAndCancel()
        session = null
        connectionJob?.cancel()
        connectionJob = null
        wantedSubscriptions.clear()
        subscriptions.update { emptySet() }
        _state.update { EventSubClientState.Disconnected }
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
        logger.debug { "[EventSub] received notification message: $message" }
        if (isDuplicateNotification(message.metadata.messageId)) {
            logger.debug { "[EventSub] skipping duplicate notification: ${message.metadata.messageId}" }
            return
        }

        val eventSubMessage =
            when (val event = message.payload.event) {
                is ChannelModerateDto -> {
                    ModerationAction(
                        id = message.metadata.messageId,
                        timestamp = message.metadata.messageTimestamp,
                        channelName = event.broadcasterUserLogin,
                        data = event,
                    )
                }

                is AutomodMessageHoldDto -> {
                    AutomodHeld(
                        id = message.metadata.messageId,
                        timestamp = message.metadata.messageTimestamp,
                        channelName = event.broadcasterUserLogin,
                        data = event,
                    )
                }

                is AutomodMessageUpdateDto -> {
                    AutomodUpdate(
                        id = message.metadata.messageId,
                        timestamp = message.metadata.messageTimestamp,
                        channelName = event.broadcasterUserLogin,
                        data = event,
                    )
                }

                is ChannelChatUserMessageHoldDto -> {
                    UserMessageHeld(
                        id = message.metadata.messageId,
                        timestamp = message.metadata.messageTimestamp,
                        channelName = event.broadcasterUserLogin,
                        data = event,
                    )
                }

                is ChannelChatUserMessageUpdateDto -> {
                    UserMessageUpdated(
                        id = message.metadata.messageId,
                        timestamp = message.metadata.messageTimestamp,
                        channelName = event.broadcasterUserLogin,
                        data = event,
                    )
                }
            }
        eventsChannel.trySend(eventSubMessage)
    }

    private fun handleRevocation(message: RevocationMessageDto) {
        logger.info { "[EventSub] received revocation message for subscription: ${message.payload.subscription}" }
        emitSystemMessage(message = "[EventSub] received revocation message for subscription: ${message.payload.subscription}")
        subscriptions.update { it.filterTo(mutableSetOf()) { sub -> sub.id != message.payload.subscription.id } }
    }

    private fun DefaultClientWebSocketSession.handleReconnect(message: ReconnectMessageDto) {
        logger.info { "[EventSub] received request to reconnect: ${message.payload.session.reconnectUrl}" }
        emitSystemMessage(message = "[EventSub] received request to reconnect")
        when (
            val url =
                message.payload.session.reconnectUrl
                    ?.replaceFirst("ws://", "wss://")
        ) {
            null -> {
                reconnect()
            }

            else -> {
                previousSession = this
                connect(url = url, twitchReconnect = true)
            }
        }
    }

    // sessions overlap during reconnects, both can deliver notifications concurrently
    private fun isDuplicateNotification(messageId: String): Boolean = synchronized(seenNotificationIds) {
        when {
            !seenNotificationIds.add(messageId) -> true

            else -> {
                if (seenNotificationIds.size > SEEN_NOTIFICATION_IDS_LIMIT) {
                    seenNotificationIds.remove(seenNotificationIds.first())
                }
                false
            }
        }
    }

    private fun emitSystemMessage(message: String) {
        val systemMessage = SystemMessage(message = message)
        eventsChannel.trySend(systemMessage)
    }

    private fun Throwable.isTransient(): Boolean = when (this) {
        is HelixApiException -> status == HttpStatusCode.TooManyRequests || status.value in 500..599
        is CancellationException -> false
        else -> true
    }

    private suspend fun DefaultClientWebSocketSession.closeAndCancel() {
        runCatching { close() }
        cancel()
    }

    @Synchronized
    private fun onConnectionLost(loopJob: Job): Boolean {
        if (connectionJob !== loopJob || !loopJob.isActive) {
            return false
        }

        // A migration that fails before its welcome falls back to a fresh session, the old one would duplicate notifications
        closePreviousConnection()
        subscriptions.update { emptySet() }
        _state.update { EventSubClientState.Disconnected }
        return true
    }

    @Synchronized
    private fun closePreviousConnection() {
        val oldSession = previousSession
        val oldJob = previousConnectionJob
        previousSession = null
        previousConnectionJob = null
        scope.launch {
            oldSession?.closeAndCancel()
            oldJob?.cancel()
        }
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
        const val SEEN_NOTIFICATION_IDS_LIMIT = 1_000
        const val MAX_JITTER = 250L
        const val RECONNECT_BASE_DELAY = 1_000L
        const val RECONNECT_MAX_ATTEMPTS = 6
        val DEFAULT_KEEPALIVE_TIMEOUT = 60.seconds
        const val SUBSCRIBE_MAX_ATTEMPTS = 3
        val SUBSCRIPTION_TIMEOUT = 5.seconds
        val SUBSCRIBE_RETRY_DELAY = 5.seconds
    }
}
