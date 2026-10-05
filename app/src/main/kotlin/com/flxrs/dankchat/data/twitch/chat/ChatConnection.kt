package com.flxrs.dankchat.data.twitch.chat

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.di.DispatchersProvider
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times

private val logger = KotlinLogging.logger("ChatConnection")

enum class ChatConnectionType {
    Read,
    Write,
}

sealed interface ChatEvent {
    data class Message(
        val message: IrcMessage,
    ) : ChatEvent

    data class Connected(
        val channel: UserName,
        val isAnonymous: Boolean,
    ) : ChatEvent

    data class ChannelNonExistent(
        val channel: UserName,
    ) : ChatEvent

    data class Error(
        val throwable: Throwable,
    ) : ChatEvent

    data object LoginFailed : ChatEvent

    data object Closed : ChatEvent

    val isDisconnected: Boolean
        get() = this is Error || this is Closed
}

@OptIn(DelicateCoroutinesApi::class)
class ChatConnection(
    private val chatConnectionType: ChatConnectionType,
    httpClient: HttpClient,
    private val authDataStore: AuthDataStore,
    dispatchersProvider: DispatchersProvider,
    private val serviceActive: StateFlow<Boolean>,
    private val url: String = IRC_URL,
    private val pongTimeout: Duration = PONG_TIMEOUT,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val client =
        httpClient.config {
            install(WebSockets)
        }

    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    private val receiveChannel = Channel<ChatEvent>(capacity = Channel.BUFFERED)

    private var awaitingPong = false

    @Volatile
    private var pongReceived: CompletableDeferred<Unit>? = null
    private var verifyJob: Job? = null

    private val channels = mutableSetOf<UserName>()
    private val channelsAttemptedToJoin = ConcurrentSet<UserName>()
    private val channelsToJoin = Channel<Collection<UserName>>(capacity = Channel.BUFFERED)
    private var currentUserName: UserName? = null
    private var currentOAuth: String? = null
    private val isAnonymous: Boolean
        get() = (currentUserName?.value.isNullOrBlank() || currentOAuth.isNullOrBlank() || currentOAuth?.startsWith("oauth:") == false)

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val messages =
        receiveChannel.receiveAsFlow().distinctUntilChanged { old, new ->
            (old.isDisconnected && new.isDisconnected) || old == new
        }

    init {
        scope.launch {
            channelsToJoin.consumeAsFlow().collect { channelsToJoin ->
                if (!_connected.value) return@collect
                val currentSession = session ?: return@collect

                channelsToJoin
                    .filter { it in channels }
                    .chunked(JOIN_CHUNK_SIZE)
                    .forEach { chunk ->
                        runCatching { currentSession.joinChannels(chunk) }
                        channelsAttemptedToJoin.addAll(chunk)
                        setupJoinCheckInterval(chunk)
                        delay(duration = chunk.size * JOIN_DELAY)
                    }
            }
        }
    }

    suspend fun sendMessage(msg: String) {
        val currentSession = session ?: return
        if (!_connected.value) return
        runCatching { currentSession.sendIrc(msg) }
    }

    fun joinChannels(channelList: List<UserName>) {
        val newChannels = channelList - channels
        channels.addAll(newChannels)

        if (_connected.value) {
            scope.launch {
                channelsToJoin.send(newChannels)
            }
        }
    }

    fun joinChannel(channel: UserName) {
        if (channel in channels) return
        channels += channel

        if (_connected.value) {
            scope.launch {
                channelsToJoin.send(listOf(channel))
            }
        }
    }

    suspend fun partChannel(channel: UserName) {
        if (channel !in channels) return

        channels.remove(channel)
        if (_connected.value) {
            val currentSession = session ?: return
            runCatching { currentSession.sendIrc("PART #$channel") }
        }
    }

    fun connect() {
        if (session?.isActive == true) return
        connectionJob?.cancel()

        currentUserName = authDataStore.userName
        currentOAuth = authDataStore.oAuthKey
        awaitingPong = false

        connectionJob =
            scope.launch(webSocketCoroutineExceptionHandler("Chat $chatConnectionType")) {
                var retryCount = 1
                while (retryCount <= RECONNECT_MAX_ATTEMPTS) {
                    var serverRequestedReconnect = false
                    try {
                        client.webSocket(url) {
                            session = this
                            _connected.value = true
                            retryCount = 1

                            val auth = currentOAuth?.takeIf { !isAnonymous } ?: "NaM"
                            val nick = currentUserName?.takeIf { !isAnonymous } ?: "justinfan12781923"
                            sendIrc("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
                            sendIrc("PASS $auth")
                            sendIrc("NICK $nick")

                            var pingJob: Job? = null
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
                                    // Any traffic proves the connection is alive
                                    pongReceived?.complete(Unit)

                                    text.removeSuffix("\r\n").split("\r\n").forEach { line ->
                                        val ircMessage = IrcMessage.parse(line)
                                        if (ircMessage.isLoginFailed()) {
                                            logger.error { "[$chatConnectionType] authentication failed with expired token, closing connection.." }
                                            receiveChannel.send(ChatEvent.LoginFailed)
                                            return@webSocket
                                        }

                                        when (ircMessage.command) {
                                            "376" -> {
                                                logger.info { "[$chatConnectionType] connected to irc" }
                                                pingJob = setupPingInterval()
                                                channelsToJoin.send(channels)
                                            }

                                            "JOIN" -> {
                                                val channel =
                                                    ircMessage.params
                                                        .getOrNull(0)
                                                        ?.substring(1)
                                                        ?.toUserName() ?: return@forEach
                                                if (channelsAttemptedToJoin.remove(channel)) {
                                                    logger.info { "[$chatConnectionType] Joined #$channel" }
                                                }
                                            }

                                            "366" -> {
                                                receiveChannel.send(ChatEvent.Connected(ircMessage.params[1].substring(1).toUserName(), isAnonymous))
                                            }

                                            "PING" -> {
                                                sendIrc("PONG :tmi.twitch.tv")
                                            }

                                            "PONG" -> {
                                                awaitingPong = false
                                            }

                                            "RECONNECT" -> {
                                                logger.info { "[$chatConnectionType] server requested reconnect" }
                                                serverRequestedReconnect = true
                                                return@webSocket
                                            }

                                            else -> {
                                                if (ircMessage.command == "NOTICE" && ircMessage.tags["msg-id"] == "msg_channel_suspended") {
                                                    channelsAttemptedToJoin.remove(ircMessage.params[0].substring(1).toUserName())
                                                }
                                                receiveChannel.send(ChatEvent.Message(ircMessage))
                                            }
                                        }
                                    }
                                }
                            } finally {
                                pingJob?.cancel()
                            }
                        }

                        _connected.value = false
                        session = null
                        channelsAttemptedToJoin.clear()
                        receiveChannel.send(ChatEvent.Closed)

                        if (!serverRequestedReconnect) {
                            logger.info { "[$chatConnectionType] connection closed" }
                            return@launch
                        }
                        logger.info { "[$chatConnectionType] reconnecting after server request" }
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        logger.error { "[$chatConnectionType] connection failed: $t" }
                        _connected.value = false
                        session = null
                        channelsAttemptedToJoin.clear()
                        receiveChannel.send(ChatEvent.Closed)

                        if (!serviceActive.value) {
                            logger.info { "[$chatConnectionType] foreground service inactive, not retrying" }
                            return@launch
                        }

                        logger.error { "[$chatConnectionType] attempting to reconnect #$retryCount.." }
                        val jitter = randomJitter()
                        val reconnectDelay = RECONNECT_BASE_DELAY * (1 shl (retryCount - 1))
                        delay(reconnectDelay + jitter)
                        retryCount = (retryCount + 1).coerceAtMost(RECONNECT_MAX_ATTEMPTS)
                    }
                }

                logger.error { "[$chatConnectionType] connection failed after $RECONNECT_MAX_ATTEMPTS retries" }
                _connected.value = false
                session = null
            }
    }

    fun close() {
        _connected.value = false
        val currentSession = session
        session = null
        channelsAttemptedToJoin.clear()
        receiveChannel.trySend(ChatEvent.Closed)
        connectionJob?.cancel()
        scope.launch {
            runCatching {
                currentSession?.close()
                currentSession?.cancel()
            }
        }
    }

    fun reconnect() {
        close()
        connect()
    }

    fun reconnectIfNecessary() {
        val currentSession = session
        if (currentSession?.isActive != true || currentSession.incoming.isClosedForReceive) {
            logger.info { "[$chatConnectionType] connection lost, reconnecting" }
            reconnect()
            return
        }

        // Quick background and foreground switches share one check, a second one would take over the pending pong
        if (verifyJob?.isActive != true) {
            verifyJob = verifyConnection(currentSession)
        }
    }

    // A session can still look active with a dead TCP socket, e.g. after the app was frozen in the background.
    private fun verifyConnection(currentSession: DefaultClientWebSocketSession) = scope.launch {
        if (!_connected.value) return@launch

        val pong = CompletableDeferred<Unit>()
        pongReceived = pong
        runCatching { currentSession.send(Frame.Text("PING\r\n")) }
        val received = withTimeoutOrNull(pongTimeout) { pong.await() }
        if (received == null && session === currentSession) {
            logger.info { "[$chatConnectionType] no pong received, reconnecting" }
            reconnect()
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

        if (_connected.value) {
            awaitingPong = true
            runCatching { currentSession.send(Frame.Text("PING\r\n")) }
        }
    }

    private fun setupJoinCheckInterval(channelsToCheck: List<UserName>) = scope.launch {
        logger.debug { "[$chatConnectionType] setting up join check for $channelsToCheck" }
        if (session?.isActive != true || !_connected.value || channelsAttemptedToJoin.isEmpty()) {
            return@launch
        }

        delay(JOIN_CHECK_DELAY)
        if (session?.isActive != true || !_connected.value) {
            channelsAttemptedToJoin.removeAll(channelsToCheck.toSet())
            return@launch
        }

        channelsToCheck.forEach {
            if (it in channelsAttemptedToJoin) {
                channelsAttemptedToJoin.remove(it)
                receiveChannel.send(ChatEvent.ChannelNonExistent(it))
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.sendIrc(msg: String) {
        send(Frame.Text("${msg.trimEnd()}\r\n"))
    }

    private suspend fun DefaultClientWebSocketSession.joinChannels(channels: Collection<UserName>) {
        if (channels.isNotEmpty()) {
            sendIrc("JOIN ${channels.joinToString(separator = ",") { "#$it" }}")
        }
    }

    companion object {
        private const val IRC_URL = "wss://irc-ws.chat.twitch.tv"
        private const val MAX_JITTER = 250L
        private val RECONNECT_BASE_DELAY = 1.seconds
        private const val RECONNECT_MAX_ATTEMPTS = 4
        private val PING_INTERVAL = 5.minutes
        private val PONG_TIMEOUT = 10.seconds
        private val JOIN_CHECK_DELAY = 10.seconds
        private val JOIN_DELAY = 600.milliseconds
        private const val JOIN_CHUNK_SIZE = 5
    }
}
