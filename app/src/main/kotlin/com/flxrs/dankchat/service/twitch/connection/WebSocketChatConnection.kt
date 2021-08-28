package com.flxrs.dankchat.service.twitch.connection

import android.util.Log
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.utils.extensions.timer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.*
import javax.inject.Inject
import kotlin.random.Random
import kotlin.random.nextLong

enum class ChatConnectionType {
    Read,
    Write
}

sealed class ChatEvent {
    data class Message(val message: IrcMessage) : ChatEvent()
    data class Connected(val channel: String, val isAnonymous: Boolean) : ChatEvent()
    data class Error(val throwable: Throwable) : ChatEvent()
    object LoginFailed : ChatEvent()
    object Closed : ChatEvent()

    val isDisconnected: Boolean
        get() = this is Error || this is Closed
}

class WebSocketChatConnection @Inject constructor(
    private val chatConnectionType: ChatConnectionType,
    private val client: OkHttpClient,
    private val scope: CoroutineScope
) {
    private var socket: WebSocket? = null
    private val request = Request.Builder().url("wss://irc-ws.chat.twitch.tv").build()
    private val receiveChannel = Channel<ChatEvent>(capacity = Channel.BUFFERED)

    private var connecting = false
    private var awaitingPong = false
    private var reconnectAttempts = 1
    private val currentReconnectDelay: Long
        get() {
            val jitter = Random.nextLong(0L..RECONNECT_MAX_JITTER)
            val reconnectDelay = RECONNECT_BASE_DELAY * (1 shl (reconnectAttempts - 1))
            reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(RECONNECT_MAX_ATTEMPTS)

            return reconnectDelay + jitter
        }


    private val channels = mutableSetOf<String>()
    private val channelsToJoin = Channel<Collection<String>>(capacity = Channel.BUFFERED)
    private var currentUserName: String? = null
    private var currentOAuth: String? = null
    private val isAnonymous: Boolean
        get() = (currentUserName.isNullOrBlank() || currentOAuth.isNullOrBlank() || currentOAuth?.startsWith("oauth:") == false)

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
                        delay(timeMillis = chunk.size * JOIN_DELAY)
                    }
            }
        }
    }

    fun sendMessage(msg: String) {
        if (connected) {
            socket?.sendMessage(msg)
        }
    }

    fun joinChannels(channelList: List<String>) {
        val newChannels = channelList - channels
        channels.addAll(newChannels)

        if (connected) {
            scope.launch {
                channelsToJoin.send(newChannels)
            }
        }
    }

    fun joinChannel(channel: String) {
        if (channel in channels) return
        channels += channel

        if (connected) {
            scope.launch {
                channelsToJoin.send(listOf(channel))
            }
        }
    }

    fun partChannel(channel: String) {
        if (channel !in channels) return

        channels.remove(channel)
        if (connected) {
            socket?.sendMessage("PART #$channel")
        }
    }

    fun connect(userName: String? = null, oAuth: String? = null) {
        if (connected || connecting) return

        currentUserName = userName
        currentOAuth = oAuth
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
            connect(currentUserName, currentOAuth)
        }
    }

    private fun setupPingInterval() = scope.timer(PING_INTERVAL) {
        if (awaitingPong) {
            cancel()
            reconnect()
            return@timer
        }

        if (connected) {
            awaitingPong = true
            socket?.sendMessage("PING")
        }
    }

    private inner class TwitchWebSocketListener : WebSocketListener() {
        private var pingJob: Job? = null

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected = false
            pingJob?.cancel()
            scope.launch { receiveChannel.send(ChatEvent.Closed) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "[$chatConnectionType] connection failed: $t")
            Log.e(TAG, "[$chatConnectionType] attempting to reconnect #${reconnectAttempts}..")
            connected = false
            connecting = false
            pingJob?.cancel()
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
                    "366"       -> scope.launch { receiveChannel.send(ChatEvent.Connected(ircMessage.params[1].substring(1), isAnonymous)) }
                    "PING"      -> webSocket.handlePing()
                    "PONG"      -> awaitingPong = false
                    "RECONNECT" -> reconnect()
                    else        -> scope.launch { receiveChannel.send(ChatEvent.Message(ircMessage)) }
                }
            }
        }
    }


    companion object {
        private const val RECONNECT_BASE_DELAY = 1_000L
        private const val RECONNECT_MAX_JITTER = 250L
        private const val RECONNECT_MAX_ATTEMPTS = 4
        private const val PING_INTERVAL = 5 * 60 * 1000L
        private const val JOIN_DELAY = 600L
        private const val JOIN_CHUNK_SIZE = 5
        private val TAG = WebSocketChatConnection::class.java.simpleName
    }
}