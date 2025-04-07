package com.flxrs.dankchat.data.api.seventv.eventapi

import android.util.Log
import com.flxrs.dankchat.BuildConfig
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
import com.flxrs.dankchat.di.WebSocketOkHttpClient
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.chat.LiveUpdatesBackgroundBehavior
import com.flxrs.dankchat.utils.AppLifecycleListener
import com.flxrs.dankchat.utils.AppLifecycleListener.AppLifecycle.Background
import com.flxrs.dankchat.utils.extensions.timer
import io.ktor.http.HttpHeaders
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Single
class SevenTVEventApiClient(
    @Named(type = WebSocketOkHttpClient::class)
    private val client: OkHttpClient,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val appLifecycleListener: AppLifecycleListener,
    defaultJson: Json,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private var socket: WebSocket? = null
    private val request = Request.Builder()
        .header(HttpHeaders.UserAgent, "dankchat/${BuildConfig.VERSION_NAME}")
        .url("wss://events.7tv.io/v3")
        .build()

    private val json = Json(defaultJson) {
        encodeDefaults = true
    }

    private var connecting = false
    private var connected = false

    private var reconnectAttempts = 1
    private var heartBeatInterval = DEFAULT_HEARTBEAT_INTERVAL
    private var lastHeartBeat = System.currentTimeMillis()
    private val currentReconnectDelay: Long
        get() {
            val jitter = Random.nextLong(0L..MAX_JITTER)
            val reconnectDelay = RECONNECT_BASE_DELAY * (1 shl (reconnectAttempts - 1))
            reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(RECONNECT_MAX_ATTEMPTS)

            return reconnectDelay + jitter
        }

    private val subscriptions = ConcurrentSet<SubscribeRequest>()
    private val _messages = MutableSharedFlow<SevenTVEventMessage>(extraBufferCapacity = 16)

    init {
        scope.launch {
            chatSettingsDataStore.debouncedSevenTvLiveEmoteUpdates
                .collectLatest { enabled ->
                    when {
                        enabled && !connected && !connecting -> start()
                        else                                 -> close()
                    }
                    if (enabled) {
                        appLifecycleListener.appState
                            .debounce(FLOW_DEBOUNCE)
                            .collectLatest { state ->
                                if (state == Background) {
                                    val timeout = when (chatSettingsDataStore.settings.first().sevenTVLiveEmoteUpdatesBehavior) {
                                        LiveUpdatesBackgroundBehavior.Always        -> return@collectLatest
                                        LiveUpdatesBackgroundBehavior.Never         -> Duration.ZERO
                                        LiveUpdatesBackgroundBehavior.FiveMinutes   -> 5.minutes
                                        LiveUpdatesBackgroundBehavior.OneHour       -> 1.hours
                                        LiveUpdatesBackgroundBehavior.OneMinute     -> 1.minutes
                                        LiveUpdatesBackgroundBehavior.ThirtyMinutes -> 30.minutes
                                    }

                                    Log.d(TAG, "[7TV Event-Api] Sleeping for $timeout until connection is closed")
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

        reconnectAttempts = 1
        attemptReconnect()
    }

    suspend fun reconnectIfNecessary() {
        if (!chatSettingsDataStore.settings.first().sevenTVLiveEmoteUpdates || connected || connecting) {
            return
        }

        reconnect()
    }

    fun close() {
        connected = false
        socket?.close(1000, null) ?: socket?.cancel()
        socket = null
    }

    private fun addSubscription(request: SubscribeRequest) {
        if (subscriptions.add(request) && connected) {
            val encoded = request.encodeOrNull() ?: return
            socket?.send(encoded)
        }
    }

    private fun removeSubscription(request: UnsubscribeRequest) {
        val subscription = subscriptions.find { it.d == request.d }
        if (subscriptions.remove(subscription) && connected) {
            val encoded = request.encodeOrNull() ?: return
            socket?.send(encoded)
        }
    }

    private fun start() {
        socket = client.newWebSocket(request, EventApiWebSocketListener())
    }

    private fun attemptReconnect() {
        scope.launch {
            delay(currentReconnectDelay)
            close()
            start()
        }
    }

    private fun setupHeartBeatInterval(): Job = scope.launch {
        delay(heartBeatInterval)
        timer(heartBeatInterval) {
            val webSocket = socket
            if (webSocket == null || System.currentTimeMillis() - lastHeartBeat > 3 * heartBeatInterval.inWholeMilliseconds) {
                cancel()
                reconnect()
                return@timer
            }
        }
    }

    private inner class EventApiWebSocketListener : WebSocketListener() {
        private var heartBeatJob: Job? = null

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "[7TV Event-Api] connection closed")
            connected = false
            heartBeatJob?.cancel()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "[7TV Event-Api] connection failed: $t")
            Log.e(TAG, "[7TV Event-Api] attempting to reconnect #${reconnectAttempts}..")
            connected = false
            connecting = false
            heartBeatJob?.cancel()

            attemptReconnect()
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            connecting = false
            reconnectAttempts = 1
            Log.i(TAG, "[7TV Event-Api] connected")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = runCatching { json.decodeFromString<DataMessage>(text) }.getOrElse {
                Log.d(TAG, "Failed to parse incoming message: ", it)
                return
            }

            when (message) {
                is HelloMessage       -> {
                    heartBeatInterval = message.d.heartBeatInterval.milliseconds
                    heartBeatJob = setupHeartBeatInterval()

                    subscriptions.forEach {
                        val encoded = it.encodeOrNull() ?: return@forEach
                        webSocket.send(encoded)
                    }
                }

                is HeartbeatMessage   -> lastHeartBeat = System.currentTimeMillis()
                is DispatchMessage    -> message.handleMessage()
                is ReconnectMessage   -> scope.launch { reconnect() }
                is EndOfStreamMessage -> Unit
                is AckMessage         -> Unit
            }
        }

    }

    private fun DispatchMessage.handleMessage() {
        when (d) {
            is EmoteSetDispatchData -> with(d.body) {
                val emoteSetId = id
                val actorName = actor.displayName
                val added = pushed?.mapNotNull {
                    it.value ?: return@mapNotNull null

                }
                val removed = pulled?.mapNotNull {
                    val removedData = it.oldValue ?: return@mapNotNull null
                    SevenTVEventMessage.EmoteSetUpdated.RemovedEmote(removedData.id, removedData.name)
                }
                val updated = updated?.mapNotNull {
                    val newData = it.value ?: return@mapNotNull null
                    val oldData = it.oldValue ?: return@mapNotNull null
                    SevenTVEventMessage.EmoteSetUpdated.UpdatedEmote(newData.id, newData.name, oldData.name)
                }
                scope.launch {
                    _messages.emit(
                        SevenTVEventMessage.EmoteSetUpdated(
                            emoteSetId = emoteSetId,
                            actorName = actorName,
                            added = added.orEmpty(),
                            removed = removed.orEmpty(),
                            updated = updated.orEmpty(),
                        )
                    )
                }
            }

            is UserDispatchData     -> with(d.body) {
                val actorName = actor.displayName
                updated?.forEach { change ->
                    val index = change.index
                    val emoteSetChange = change.value?.filterIsInstance<EmoteSetChangeField>()?.firstOrNull() ?: return
                    scope.launch {
                        _messages.emit(
                            SevenTVEventMessage.UserUpdated(
                                actorName = actorName,
                                connectionIndex = index,
                                emoteSetId = emoteSetChange.value.id,
                                oldEmoteSetId = emoteSetChange.oldValue.id
                            )
                        )
                    }
                }
            }
        }
    }

    private inline fun <reified T> T.encodeOrNull(): String? {
        return runCatching { json.encodeToString(this) }.getOrNull()
    }

    companion object {
        private const val MAX_JITTER = 250L
        private const val RECONNECT_BASE_DELAY = 1_000L
        private const val RECONNECT_MAX_ATTEMPTS = 6
        private val DEFAULT_HEARTBEAT_INTERVAL = 25.seconds
        private val FLOW_DEBOUNCE = 2.seconds
        private val TAG = SevenTVEventApiClient::class.java.simpleName
    }
}
