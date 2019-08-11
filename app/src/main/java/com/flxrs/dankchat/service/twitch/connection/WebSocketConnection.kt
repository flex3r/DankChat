package com.flxrs.dankchat.service.twitch.connection

import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.utils.timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketConnection(
    private val scope: CoroutineScope,
    private val onDisconnect: () -> Unit,
    private val onMessage: (IrcMessage) -> Unit
) {

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val request = Request.Builder()
        .url("wss://irc-ws.chat.twitch.tv")
        .build()

    private var nick = ""
    private var oAuth = ""
    private val channels = mutableSetOf<String>()

    private var readerWebSocket: WebSocket? = null
    private var writerWebSocket: WebSocket? = null
    private var readerConnected = false
    private var writerConnected = false
    private var readerPong = false
    private var writerPong = false
    private var connecting = false

    var isJustinFan = false
    private var onClosed: (() -> Unit)? = null

    fun sendMessage(msg: String) {
        if (readerConnected && writerConnected) {
            writerWebSocket?.sendMessage(msg)
        }
    }

    fun joinChannel(channel: String) {
        if (!channels.contains(channel)) {
            channels.add(channel)
            if (readerConnected && writerConnected) {
                writerWebSocket?.sendMessage("JOIN #$channel")
                readerWebSocket?.sendMessage("JOIN #$channel")
            }
        }
    }

    fun partChannel(channel: String) {
        if (channels.contains(channel)) {
            channels.remove(channel)
            if (readerConnected && writerConnected) {
                writerWebSocket?.sendMessage("PART #$channel")
                readerWebSocket?.sendMessage("PART #$channel")
            }
        }
    }

    @Synchronized
    fun connect(nick: String, oAuth: String) {
        if (!readerConnected && !writerConnected) {
            this.nick = nick
            this.oAuth = oAuth
            isJustinFan = (oAuth.isBlank() || !oAuth.startsWith("oauth:"))
            connecting = true

            readerWebSocket = client.newWebSocket(request, ReaderWebSocketListener())
            writerWebSocket = client.newWebSocket(request, WriterWebSocketListener())
        }
    }

    @Synchronized
    fun close(onClosed: (() -> Unit)?) {
        this.onClosed = onClosed
        scope.coroutineContext.cancel()
        readerConnected = false
        writerConnected = false
        writerWebSocket?.close(1000, null)
        readerWebSocket?.close(1000, null)
    }

    @Synchronized
    fun reconnect(onlyIfNecessary: Boolean = false) {
        if (!onlyIfNecessary || (!readerConnected && !writerConnected && !connecting)) {
            close { connect(nick, oAuth) }
        }
    }

    private fun WebSocket.sendMessage(msg: String) {
        send("${msg.trimEnd()}\r\n")
    }

    private fun WebSocket.joinCurrentChannels() {
        for (channel in channels) {
            sendMessage("JOIN #$channel")
        }
    }

    private fun WebSocket.handlePing() {
        sendMessage("PONG :tmi.twitch.tv")
    }

    private fun setupReaderPingInterval() = scope.timer(5 * 60 * 1000) {
        if (readerPong) {
            cancel()
            reconnect()
            return@timer
        }

        if (readerConnected) {
            readerWebSocket?.sendMessage("PING")
            readerPong = true
        }
    }

    private fun setupWriterPingInterval() = scope.timer(5 * 60 * 1000) {
        if (writerPong) {
            cancel()
            reconnect()
            return@timer
        }

        if (writerConnected) {
            writerWebSocket?.sendMessage("PING")
            writerPong = true
        }
    }

    private inner class ReaderWebSocketListener : WebSocketListener() {

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            readerConnected = false
            onDisconnect()
            onClosed?.invoke()
            onClosed = null
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            readerConnected = false
            scope.coroutineContext.cancel()
            onDisconnect()
            connect(nick, oAuth)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val splits = text.split("\r\n")
            val size = splits.size

            splits.forEachIndexed { i, line ->
                if (i != size - 1) {
                    val ircMessage = IrcMessage.parse(line)
                    when (ircMessage.command) {
                        "PING" -> webSocket.handlePing()
                        "PONG" -> readerPong = false
                        "RECONNECT" -> reconnect()
                        else -> onMessage(ircMessage)
                    }
                }
            }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            readerConnected = true
            connecting = false
            val auth = if (isJustinFan) "NaM" else oAuth
            val nick = if (nick.isBlank() || auth == "NaM") "justinfan12781923" else nick

            webSocket.sendMessage("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
            webSocket.sendMessage("PASS $auth")
            webSocket.sendMessage("NICK $nick")
            webSocket.joinCurrentChannels()
            setupReaderPingInterval()
        }
    }

    private inner class WriterWebSocketListener : WebSocketListener() {

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            writerConnected = false
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            writerConnected = false
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val splits = text.split("\r\n")
            val size = splits.size

            splits.forEachIndexed { i, line ->
                if (i != size - 1) {
                    val ircMessage = IrcMessage.parse(line)
                    when (ircMessage.command) {
                        "PING" -> webSocket.handlePing()
                        "PONG" -> writerPong = false
                        "RECONNECT" -> reconnect()
                        else -> Unit
                    }

                }
            }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            writerConnected = true
            val auth = if (isJustinFan) "NaM" else oAuth
            val nick = if (nick.isBlank() || auth == "NaM") "justinfan12781923" else nick

            webSocket.sendMessage("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
            webSocket.sendMessage("PASS $auth")
            webSocket.sendMessage("NICK $nick")
            webSocket.joinCurrentChannels()
            setupWriterPingInterval()
        }
    }
}
