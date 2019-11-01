package com.flxrs.dankchat.service.twitch.connection

import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.utils.extensions.timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong

class WebSocketConnection(
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
    private var reconnectAttempts = 0
    private var onClosed: (() -> Unit)? = null

    var isAnonymous = false

    fun sendMessage(msg: String) {
        if (connected) {
            socket?.sendMessage(msg)
        }
    }

    fun joinChannel(channel: String) {
        if (!channels.contains(channel)) {
            channels.add(channel)
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
            reconnectAttempts = 0
            attemptReconnect(forceConnect)
        }
    }

    private fun attemptReconnect(forceConnect: Boolean = false) {
        scope.launch {
            var reconnectDelay = 150L
            if (reconnectAttempts > 0) {
                val jitter = floor(Math.random() * (RECONNECT_JITTER + 1))
                reconnectDelay = (min(
                    (RECONNECT_MULTIPLIER * reconnectAttempts) - RECONNECT_JITTER,
                    5000
                ) + jitter).roundToLong()
            }
            reconnectAttempts++
            delay(reconnectDelay)

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

            attemptReconnect(true)
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            connecting = false
            reconnectAttempts = 0
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
                when (ircMessage.command) {
                    "376"       -> {
                        socket?.joinChannels(channels)
                        pingJob = setupPingInterval()
                    }
                    "PING"      -> webSocket.handlePing()
                    "PONG"      -> awaitingPong = false
                    "RECONNECT" -> reconnect()
                    else        -> onMessage(ircMessage)
                }
            }
        }
    }


    companion object {
        private const val RECONNECT_MULTIPLIER = 250
        private const val RECONNECT_JITTER = 100
        private const val PING_INTERVAL = 5 * 60 * 1000L
    }
}