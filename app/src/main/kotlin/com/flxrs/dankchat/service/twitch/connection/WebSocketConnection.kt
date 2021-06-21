package com.flxrs.dankchat.service.twitch.connection

import android.util.Log
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.utils.extensions.timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import kotlin.random.Random
import kotlin.random.nextLong

class WebSocketConnection(
    private val connectionName: String,
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
    private val request: Request,
    private val onDisconnect: (() -> Unit)? = null,
    private val onMessage: (IrcMessage) -> Unit
) {
    private var nick = ""
    private var oAuth = ""
    private val channels = mutableSetOf<String>()

    private var socket: WebSocket? = null
    private var connected = false
    private var awaitingPong = false
    private var connecting = false
    private var reconnectAttempts = 1
    private var onClosed: (() -> Unit)? = null

    private val currentReconnectDelay: Long
        get() {
            val jitter = Random.nextLong(0L..RECONNECT_MAX_JITTER)
            val reconnectDelay = RECONNECT_BASE_DELAY * (1 shl (reconnectAttempts - 1))
            reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(RECONNECT_MAX_ATTEMPTS)

            return reconnectDelay + jitter
        }

    var isAnonymous = false

    fun sendMessage(msg: String) {
        if (connected) {
            socket?.sendMessage(msg)
        }
    }

    fun joinChannels(channelList: List<String>) {
        val channelsToJoin = channelList - channels
        channels.addAll(channelsToJoin)
        if (connected) {
            socket?.joinChannels(channels)
        }
    }

    fun joinChannel(channel: String) {
        if (!channels.contains(channel)) {
            channels += channel
            if (connected) {
                socket?.sendMessage("JOIN #$channel")
            }
        }
    }

    fun partChannel(channel: String) {
        if (channels.contains(channel)) {
            channels.remove(channel)
            if (connected) {
                socket?.sendMessage("PART #$channel")
            }
        }
    }

    fun connect(nick: String, oAuth: String, forceConnect: Boolean = false) {
        if (forceConnect || (!connected && !connecting)) {
            this.nick = nick
            this.oAuth = oAuth
            awaitingPong = false
            connecting = true

            socket = client.newWebSocket(request, TwitchWebSocketListener())
        }
    }

    fun close(onClosed: (() -> Unit)?): Boolean {
        this.onClosed = onClosed
        connected = false
        return socket?.close(1000, null) ?: false
    }

    fun reconnect(onlyIfNecessary: Boolean = false, forceConnect: Boolean = false) {
        if (!onlyIfNecessary || (!connected && !connecting)) {
            reconnectAttempts = 1
            attemptReconnect(forceConnect)
        }
    }

    private fun attemptReconnect(forceConnect: Boolean = false) {
        scope.launch {
            delay(currentReconnectDelay)

            if (forceConnect) {
                connect(nick, oAuth, forceConnect)
            } else {
                if (!close { connect(nick, oAuth) }) {
                    connect(nick, oAuth, true)
                }
            }
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
            onDisconnect?.invoke()
            onClosed?.invoke()
            onClosed = null
            pingJob?.cancel()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connected = false
            connecting = false
            onDisconnect?.invoke()
            pingJob?.cancel()

            Log.e(TAG, "[$connectionName] connection failed: ${t.stackTraceToString()}, attempting to reconnect #${reconnectAttempts}.. ")
            attemptReconnect(true)
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            connecting = false
            reconnectAttempts = 1
            isAnonymous = (oAuth.isBlank() || !oAuth.startsWith("oauth:"))

            val auth = if (isAnonymous) "NaM" else oAuth
            val nick = if (nick.isBlank() || isAnonymous) "justinfan12781923" else nick

            webSocket.sendMessage("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
            webSocket.sendMessage("PASS $auth")
            webSocket.sendMessage("NICK $nick")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            text.removeSuffix("\r\n").split("\r\n").forEach { line ->
                val ircMessage = IrcMessage.parse(line)
                if (ircMessage.isLoginFailed()) {
                    Log.e(TAG, "[$connectionName] authentication failed with expired token, closing connection..")
                    close(null)
                }
                when (ircMessage.command) {
                    "376" -> {
                        Log.i(TAG, "[$connectionName] connected to irc")
                        socket?.joinChannels(channels)
                        pingJob = setupPingInterval()
                    }
                    "PING" -> webSocket.handlePing()
                    "PONG" -> awaitingPong = false
                    "RECONNECT" -> reconnect()
                    else -> onMessage(ircMessage)
                }
            }
        }
    }


    companion object {
        private const val RECONNECT_BASE_DELAY = 1_000L
        private const val RECONNECT_MAX_JITTER = 250L
        private const val RECONNECT_MAX_ATTEMPTS = 4
        private const val PING_INTERVAL = 5 * 60 * 1000L
        private val TAG = WebSocketConnection::class.java.simpleName
    }
}