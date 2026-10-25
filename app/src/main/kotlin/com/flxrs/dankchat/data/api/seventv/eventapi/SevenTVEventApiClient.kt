package com.flxrs.dankchat.data.api.seventv.eventapi

import com.flxrs.dankchat.data.api.seventv.eventapi.dto.AckMessage
import com.flxrs.dankchat.data.api.seventv.eventapi.dto.DataMessage
import com.flxrs.dankchat.data.api.seventv.eventapi.dto.DispatchMessage
import com.flxrs.dankchat.data.api.seventv.eventapi.dto.EmoteSetChangeField
import com.flxrs.dankchat.data.api.seventv.eventapi.dto.EmoteSetDispatchData
import com.flxrs.dankchat.data.api.seventv.eventapi.dto.EndOfStreamMessage
import com.flxrs.dankchat.data.api.seventv.eventapi.dto.HeartbeatMessage
import com.flxrs.dankchat.data.api.seventv.eventapi.dto.HelloMessage
import com.flxrs.dankchat.data.api.seventv.eventapi.dto.ReconnectMessage
import com.flxrs.dankchat.data.api.seventv.eventapi.dto.SubscribeRequest
import com.flxrs.dankchat.data.api.seventv.eventapi.dto.UnsubscribeRequest
import com.flxrs.dankchat.data.api.seventv.eventapi.dto.UserDispatchData
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.battery.BatterySettingsDataStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.utils.AppLifecycleListener
import com.flxrs.dankchat.utils.AppLifecycleListener.AppLifecycle.Background
import com.flxrs.dankchat.utils.ForegroundServiceState
import com.flxrs.dankchat.utils.extensions.timer
import com.flxrs.dankchat.utils.webSocketCoroutineExceptionHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.util.collections.ConcurrentSet
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger("SevenTVEventApiClient")

@Single
class SevenTVEventApiClient(
    httpClient: HttpClient,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val batterySettingsDataStore: BatterySettingsDataStore,
    private val appLifecycleListener: AppLifecycleListener,
    private val foregroundServiceState: ForegroundServiceState,
    defaultJson: Json,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val client =
        httpClient.config {
            install(WebSockets)
        }

    private val json =
        Json(defaultJson) {
            encodeDefaults = true
        }

    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    @Volatile
    private var lastHeartBeat: TimeMark = TimeSource.Monotonic.markNow()

    private val subscriptions = ConcurrentSet<SubscribeRequest>()
    private val _messages = MutableSharedFlow<SevenTVEventMessage>(extraBufferCapacity = 16)

    private val connected: Boolean
        get() = session?.isActive == true && session?.incoming?.isClosedForReceive == false

    init {
        scope.launch {
            chatSettingsDataStore.debouncedSevenTvLiveEmoteUpdates
                .collectLatest { enabled ->
                    when {
                        enabled -> start()
                        else -> close()
                    }
                    if (enabled) {
                        appLifecycleListener.appState
                            .debounce(FLOW_DEBOUNCE)
                            .collectLatest { state ->
                                if (state == Background) {
                                    val batterySettings = batterySettingsDataStore.settings.first()
                                    if (!batterySettings.pauseSevenTvLiveUpdates) {
                                        return@collectLatest
                                    }

                                    val timeout = batterySettings.backgroundDelay.duration
                                    logger.debug { "[7TV Event-Api] Sleeping for $timeout until connection is closed" }
                                    delay(timeout)
                                    close()
                                }
                            }
                    }
                }
        }
    }

    val messages = _messages.asSharedFlow()

    suspend fun subscribeUser(userId: String) {
        if (!chatSettingsDataStore.settings.first().sevenTVLiveEmoteUpdates) {
            return
        }

        val request = SubscribeRequest.userUpdates(userId)
        addSubscription(request)
    }

    suspend fun subscribeEmoteSet(emoteSetId: String) {
        if (!chatSettingsDataStore.settings.first().sevenTVLiveEmoteUpdates) {
            return
        }

        val request = SubscribeRequest.emoteSetUpdates(emoteSetId)
        addSubscription(request)
    }

    suspend fun unsubscribeUser(userId: String) {
        if (!chatSettingsDataStore.settings.first().sevenTVLiveEmoteUpdates) {
            return
        }

        val request = UnsubscribeRequest.userUpdates(userId)
        removeSubscription(request)
    }

    suspend fun unsubscribeEmoteSet(emoteSetId: String) {
        if (!chatSettingsDataStore.settings.first().sevenTVLiveEmoteUpdates) {
            return
        }

        val request = UnsubscribeRequest.emoteSetUpdates(emoteSetId)
        removeSubscription(request)
    }

    suspend fun reconnect() {
        if (!chatSettingsDataStore.settings.first().sevenTVLiveEmoteUpdates) {
            return
        }

        close()
        start()
    }

    suspend fun reconnectIfNecessary() {
        if (connected || connectionJob?.isActive == true) {
            return
        }

        reconnect()
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

    private fun addSubscription(request: SubscribeRequest) {
        if (subscriptions.add(request) && connected) {
            val encoded = request.encodeOrNull() ?: return
            val currentSession = session
            scope.launch { runCatching { currentSession?.send(Frame.Text(encoded)) } }
        }
    }

    private fun removeSubscription(request: UnsubscribeRequest) {
        val subscription = subscriptions.find { it.d == request.d }
        if (subscriptions.remove(subscription) && connected) {
            val encoded = request.encodeOrNull() ?: return
            val currentSession = session
            scope.launch { runCatching { currentSession?.send(Frame.Text(encoded)) } }
        }
    }

    private fun start() {
        if (connected || connectionJob?.isActive == true) {
            return
        }

        logger.info { "[7TV Event-Api] connecting" }
        connectionJob =
            scope.launch(webSocketCoroutineExceptionHandler("7TV Event-Api")) {
                var retryCount = 1
                while (isActive) {
                    var serverRequestedReconnect = false
                    try {
                        client.webSocket(EVENT_API_URL) {
                            session = this
                            retryCount = 1
                            logger.info { "[7TV Event-Api] connected" }

                            var heartBeatJob: Job? = null
                            try {
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

                                    val message =
                                        runCatching { json.decodeFromString<DataMessage>(text) }.getOrElse {
                                            logger.debug(it) { "Failed to parse incoming message" }
                                            continue
                                        }

                                    when (message) {
                                        is HelloMessage -> {
                                            lastHeartBeat = TimeSource.Monotonic.markNow()
                                            heartBeatJob?.cancel()
                                            heartBeatJob = setupHeartBeatWatchdog(message.d.heartBeatInterval.milliseconds)

                                            subscriptions.forEach { subscription ->
                                                val encoded = subscription.encodeOrNull() ?: return@forEach
                                                send(Frame.Text(encoded))
                                            }
                                        }

                                        is HeartbeatMessage -> {
                                            lastHeartBeat = TimeSource.Monotonic.markNow()
                                        }

                                        is DispatchMessage -> {
                                            message.handleMessage()
                                        }

                                        is ReconnectMessage -> {
                                            serverRequestedReconnect = true
                                            return@webSocket
                                        }

                                        is EndOfStreamMessage -> Unit

                                        is AckMessage -> Unit
                                    }
                                }
                            } finally {
                                heartBeatJob?.cancel()
                            }
                        }

                        session = null
                        if (!foregroundServiceState.active.value) {
                            logger.info { "[7TV Event-Api] connection closed, foreground service inactive, not reconnecting" }
                            return@launch
                        }

                        when {
                            serverRequestedReconnect -> logger.info { "[7TV Event-Api] reconnecting after server request" }

                            else -> {
                                // Graceful server close (close frame/END_OF_STREAM). Reconnecting is allowed here because
                                // the background limit collector cancels this loop via close() once the limit expires.
                                logger.info { "[7TV Event-Api] connection closed by server, reconnecting" }
                                delay(RECONNECT_BASE_DELAY + randomJitter())
                            }
                        }
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        logger.error { "[7TV Event-Api] connection failed: $t" }
                        session = null

                        if (!foregroundServiceState.active.value) {
                            logger.info { "[7TV Event-Api] foreground service inactive, not retrying" }
                            return@launch
                        }

                        logger.info { "[7TV Event-Api] attempting to reconnect #$retryCount.." }
                        val reconnectDelay = RECONNECT_BASE_DELAY * (1 shl (retryCount - 1))
                        delay(reconnectDelay + randomJitter())
                        retryCount = (retryCount + 1).coerceAtMost(RECONNECT_MAX_ATTEMPTS)
                    }
                }
            }
    }

    private fun setupHeartBeatWatchdog(interval: Duration): Job = scope.timer(interval) {
        if (lastHeartBeat.elapsedNow() > interval * 3) {
            logger.info { "[7TV Event-Api] missed heartbeats, reconnecting" }
            cancel()
            reconnect()
        }
    }

    private fun randomJitter() = Random.nextLong(range = 0L..MAX_JITTER).milliseconds

    private suspend fun DispatchMessage.handleMessage() {
        when (d) {
            is EmoteSetDispatchData -> {
                with(d.body) {
                    val emoteSetId = id
                    val actorName = actor.displayName
                    val added =
                        pushed?.mapNotNull {
                            it.value ?: return@mapNotNull null
                        }
                    val removed =
                        pulled?.mapNotNull {
                            val removedData = it.oldValue ?: return@mapNotNull null
                            SevenTVEventMessage.EmoteSetUpdated.RemovedEmote(removedData.id, removedData.name)
                        }
                    val updated =
                        updated?.mapNotNull {
                            val newData = it.value ?: return@mapNotNull null
                            val oldData = it.oldValue ?: return@mapNotNull null
                            SevenTVEventMessage.EmoteSetUpdated.UpdatedEmote(newData.id, newData.name, oldData.name)
                        }
                    _messages.emit(
                        SevenTVEventMessage.EmoteSetUpdated(
                            emoteSetId = emoteSetId,
                            actorName = actorName,
                            added = added.orEmpty(),
                            removed = removed.orEmpty(),
                            updated = updated.orEmpty(),
                        ),
                    )
                }
            }

            is UserDispatchData -> {
                with(d.body) {
                    val actorName = actor.displayName
                    updated?.forEach { change ->
                        val index = change.index
                        val emoteSetChange = change.value?.filterIsInstance<EmoteSetChangeField>()?.firstOrNull() ?: return@forEach
                        _messages.emit(
                            SevenTVEventMessage.UserUpdated(
                                actorName = actorName,
                                connectionIndex = index,
                                emoteSetId = emoteSetChange.value.id,
                                oldEmoteSetId = emoteSetChange.oldValue.id,
                            ),
                        )
                    }
                }
            }
        }
    }

    private inline fun <reified T> T.encodeOrNull(): String? = runCatching { json.encodeToString(this) }.getOrNull()

    data class Status(
        val connected: Boolean,
        val subscriptionCount: Int,
    )

    fun status(): Status = Status(connected = connected, subscriptionCount = subscriptions.size)

    companion object {
        private const val EVENT_API_URL = "wss://events.7tv.io/v3"
        private const val MAX_JITTER = 250L
        private val RECONNECT_BASE_DELAY = 1.seconds
        private const val RECONNECT_MAX_ATTEMPTS = 6
        private val FLOW_DEBOUNCE = 2.seconds
    }
}
