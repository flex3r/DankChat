package com.flxrs.dankchat.data.api.bttv.liveupdates

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.utils.ForegroundServiceState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.annotation.Single
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger("BTTVLiveUpdateClient")

@Single
class BTTVLiveUpdateClient(
    httpClient: HttpClient,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val foregroundServiceState: ForegroundServiceState,
    defaultJson: Json,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val client = httpClient.config { install(WebSockets) }
    private val json = Json(defaultJson) {}
    private val subscriptions = ConcurrentSet<UserId>()
    private val _events = MutableSharedFlow<BTTVLiveUpdateEvent>(extraBufferCapacity = 16)

    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    private val connected: Boolean
        get() = session?.isActive == true && session?.incoming?.isClosedForReceive == false

    val events = _events.asSharedFlow()

    init {
        scope.launch {
            chatSettingsDataStore.debouncedBttvLiveEmoteUpdates.collectLatest { enabled ->
                when {
                    enabled && subscriptions.isNotEmpty() -> start()
                    !enabled -> close()
                }
            }
        }
        scope.launch {
            foregroundServiceState.active.collectLatest { active ->
                when {
                    active && chatSettingsDataStore.current().bttvLiveEmoteUpdates && subscriptions.isNotEmpty() -> start()
                    !active -> close()
                }
            }
        }
    }

    suspend fun subscribeChannel(channelId: UserId) {
        val added = subscriptions.add(channelId)
        if (!chatSettingsDataStore.settings.first().bttvLiveEmoteUpdates) return

        if (added) {
            runCatching { session?.send(Frame.Text(subscriptionMessage("join_channel", channelId))) }
        }
        start()
    }

    fun unsubscribeChannel(channelId: UserId) {
        if (!subscriptions.remove(channelId)) return

        val currentSession = session
        scope.launch { runCatching { currentSession?.send(Frame.Text(subscriptionMessage("part_channel", channelId))) } }
        if (subscriptions.isEmpty()) close()
    }

    fun reconnect() {
        if (!chatSettingsDataStore.current().bttvLiveEmoteUpdates || subscriptions.isEmpty()) return
        close()
        start()
    }

    fun reconnectIfNecessary() {
        if (connected || connectionJob?.isActive == true) return
        reconnect()
    }

    fun close() {
        val currentSession = session
        session = null
        connectionJob?.cancel()
        connectionJob = null
        scope.launch { runCatching { currentSession?.close() } }
    }

    @Synchronized
    private fun start() {
        if (connected || connectionJob?.isActive == true || !shouldReconnect()) {
            return
        }

        connectionJob =
            scope.launch(webSocketCoroutineExceptionHandler("BTTV Live Updates")) {
                var retryCount = 1
                while (isActive) {
                    try {
                        client.webSocket(LIVE_UPDATE_URL) {
                            val currentSession = this
                            session = currentSession
                            try {
                                retryCount = 1
                                logger.info { "[BTTV Live Updates] connected" }
                                subscriptions.forEach { channelId -> send(Frame.Text(subscriptionMessage("join_channel", channelId))) }

                                while (isActive) {
                                    val result = incoming.receiveCatching()
                                    val frame = result.getOrNull()
                                    if (frame == null) {
                                        result.exceptionOrNull()?.let { throw it }
                                        return@webSocket
                                    }
                                    val text = (frame as? Frame.Text)?.readText() ?: continue
                                    val event = json.decodeBTTVLiveUpdateEvent(text) ?: continue
                                    _events.emit(event)
                                }
                            } finally {
                                if (session === currentSession) session = null
                            }
                        }
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        logger.warn(t) { "[BTTV Live Updates] connection failed" }
                    }

                    if (!shouldReconnect()) return@launch

                    val reconnectDelay = RECONNECT_BASE_DELAY * (1 shl (retryCount - 1))
                    delay(reconnectDelay + Random.nextLong(0L..MAX_JITTER).milliseconds)
                    retryCount = (retryCount + 1).coerceAtMost(RECONNECT_MAX_ATTEMPTS)
                }
            }
    }

    private fun shouldReconnect() = foregroundServiceState.active.value &&
        chatSettingsDataStore.current().bttvLiveEmoteUpdates &&
        subscriptions.isNotEmpty()

    private fun subscriptionMessage(
        name: String,
        channelId: UserId,
    ): String = buildJsonObject {
        put("name", name)
        put(
            "data",
            buildJsonObject {
                put("name", "$TWITCH_CHANNEL_PREFIX$channelId")
            },
        )
    }.toString()

    private companion object {
        const val LIVE_UPDATE_URL = "wss://sockets.betterttv.net/ws"
        val RECONNECT_BASE_DELAY = 2.seconds
        const val RECONNECT_MAX_ATTEMPTS = 5
        const val MAX_JITTER = 1_000L
    }
}
