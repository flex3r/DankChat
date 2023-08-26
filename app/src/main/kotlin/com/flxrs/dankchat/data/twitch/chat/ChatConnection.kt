package com.flxrs.dankchat.data.twitch.chat

import android.util.Log
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.timer
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times

enum class ChatConnectionType {
    Read,
    Write
}

sealed interface ChatEvent {
    data class Message(val message: IrcMessage) : ChatEvent
    data class Connected(val channel: UserName, val isAnonymous: Boolean) : ChatEvent
    data class ChannelNonExistent(val channel: UserName) : ChatEvent
    data class Error(val throwable: Throwable) : ChatEvent
    data object LoginFailed : ChatEvent
    data object Closed : ChatEvent

    val isDisconnected: Boolean
        get() = this is Error || this is Closed
}

class ChatConnection @Inject constructor(
    private val chatConnectionType: ChatConnectionType,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val preferences: DankChatPreferenceStore,
) {
    private var socket: WebSocket? = null
    private val request = Request.Builder().url("wss://irc-ws.chat.twitch.tv").build()
    private val receiveChannel = Channel<ChatEvent>(capacity = Channel.BUFFERED)

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

    private val channels = mutableSetOf<UserName>()
    private val channelsAttemptedToJoin = ConcurrentSet<UserName>()
    private val channelsToJoin = Channel<Collection<UserName>>(capacity = Channel.BUFFERED)
    private var currentUserName: UserName? = null
    private var currentOAuth: String? = null
    private val isAnonymous: Boolean
        get() = (currentUserName?.value.isNullOrBlank() || currentOAuth.isNullOrBlank() || currentOAuth?.startsWith("oauth:") == false)

    var connected = false
        private set

    val messages = receiveChannel.receiveAsFlow().distinctUntilChanged { old, new ->
        (old.isDisconnected && new.isDisconnected) || old == new
    }

    init {
        scope.launch {
            channelsToJoin.consumeAsFlow().collect { channelsToJoin ->
                if (!connected) return@collect

                channelsToJoin.filter { it in channels }
                    .chunked(JOIN_CHUNK_SIZE)
                    .forEach { chunk ->
                        socket?.joinChannels(chunk)
                        channelsAttemptedToJoin.addAll(chunk)
                        setupJoinCheckInterval(chunk)
                        delay(duration = chunk.size * JOIN_DELAY)
                    }
            }
        }
    }

    fun sendMessage(msg: String) {
        if (connected) {
            socket?.sendMessage(msg)
        }
    }

    fun joinChannels(channelList: List<UserName>) {
        val newChannels = channelList - channels
        channels.addAll(newChannels)

        if (connected) {
            scope.launch {
                channelsToJoin.send(newChannels)
            }
        }
    }

    fun joinChannel(channel: UserName) {
        if (channel in channels) return
        channels += channel

        if (connected) {
            scope.launch {
                channelsToJoin.send(listOf(channel))
            }
        }
    }

    fun partChannel(channel: UserName) {
        if (channel !in channels) return

        channels.remove(channel)
        if (connected) {
            socket?.sendMessage("PART #$channel")
        }
    }

    fun connect() {
        if (connected || connecting) return

        currentUserName = preferences.userName
        currentOAuth = preferences.oAuthKey
        awaitingPong = false
        connecting = true
        socket = client.newWebSocket(request, TwitchWebSocketListener())
    }

    fun close() {
        connected = false
        socket?.close(1000, null) ?: socket?.cancel()
    }

    fun reconnect() {
        reconnectAttempts = 1
        attemptReconnect()
    }

    fun reconnectIfNecessary() {
        if (connected || connecting) return
        reconnect()
    }

    private fun attemptReconnect() {
        scope.launch {
            delay(currentReconnectDelay)
            close()
            connect()
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
            webSocket.sendMessage("PING")
        }
    }

    private fun setupJoinCheckInterval(channelsToCheck: List<UserName>) = scope.launch {
        Log.d(TAG, "[$chatConnectionType] setting up join check for $channelsToCheck")
        // only send a ChannelNonExistent event if we are actually connected or there are attempted joins
        if (socket == null || !connected || channelsAttemptedToJoin.isEmpty()) {
            return@launch
        }

        delay(JOIN_CHECK_DELAY)
        if (socket == null || !connected) {
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

    private inner class TwitchWebSocketListener : WebSocketListener() {
        private var pingJob: Job? = null

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected = false
            pingJob?.cancel()
            channelsAttemptedToJoin.clear()
            scope.launch { receiveChannel.send(ChatEvent.Closed) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "[$chatConnectionType] connection failed: $t")
            Log.e(TAG, "[$chatConnectionType] attempting to reconnect #${reconnectAttempts}..")
            connected = false
            connecting = false
            pingJob?.cancel()
            channelsAttemptedToJoin.clear()
            scope.launch { receiveChannel.send(ChatEvent.Closed) }

            attemptReconnect()
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            connecting = false
            reconnectAttempts = 1

            val auth = currentOAuth?.takeIf { !isAnonymous } ?: "NaM"
            val nick = currentUserName?.takeIf { !isAnonymous } ?: "justinfan12781923"

            webSocket.sendMessage("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
            webSocket.sendMessage("PASS $auth")
            webSocket.sendMessage("NICK $nick")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            text.removeSuffix("\r\n").split("\r\n").forEach { line ->
                val ircMessage = IrcMessage.parse(line)
                if (ircMessage.isLoginFailed()) {
                    Log.e(TAG, "[$chatConnectionType] authentication failed with expired token, closing connection..")
                    scope.launch { receiveChannel.send(ChatEvent.LoginFailed) }
                    close()
                }
                when (ircMessage.command) {
                    "376"       -> {
                        Log.i(TAG, "[$chatConnectionType] connected to irc")
                        pingJob = setupPingInterval()

                        scope.launch {
                            channelsToJoin.send(channels)
                        }
                    }

                    "JOIN"      -> {
                        if (ircMessage.prefix.startsWith(currentUserName?.value.orEmpty())) {
                            val channel = ircMessage.params[0].substring(1).toUserName()
                            Log.i(TAG, "[$chatConnectionType] Joined #$channel")
                            channelsAttemptedToJoin.remove(channel)
                        }
                    }

                    "366"       -> scope.launch { receiveChannel.send(ChatEvent.Connected(ircMessage.params[1].substring(1).toUserName(), isAnonymous)) }
                    "PING"      -> webSocket.handlePing()
                    "PONG"      -> awaitingPong = false
                    "RECONNECT" -> reconnect()
                    else        -> {
                        if (ircMessage.command == "NOTICE" && ircMessage.tags["msg-id"] == "msg_channel_suspended") {
                            channelsAttemptedToJoin.remove(ircMessage.params[0].substring(1).toUserName())
                        }

                        scope.launch { receiveChannel.send(ChatEvent.Message(ircMessage)) }
                    }
                }
            }
        }
    }

    private fun WebSocket.sendMessage(msg: String) {
        send("${msg.trimEnd()}\r\n")
    }

    private fun WebSocket.handlePing() {
        sendMessage("PONG :tmi.twitch.tv")
    }

    private fun WebSocket.joinChannels(channels: Collection<UserName>) {
        if (channels.isNotEmpty()) {
            sendMessage("JOIN ${channels.joinToString(separator = ",") { "#$it" }}")
        }
    }

    companion object {
        private const val MAX_JITTER = 250L
        private val RECONNECT_BASE_DELAY = 1.seconds
        private const val RECONNECT_MAX_ATTEMPTS = 4
        private val PING_INTERVAL = 5.minutes
        private val JOIN_CHECK_DELAY = 10.seconds
        private val JOIN_DELAY = 600.milliseconds
        private const val JOIN_CHUNK_SIZE = 5
        private val TAG = ChatConnection::class.java.simpleName
    }
}
