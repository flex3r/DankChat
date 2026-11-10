package com.flxrs.dankchat.data.twitch.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MockIrcServer : AutoCloseable {
    private val server = MockWebServer()

    @Volatile
    var respondToPing = true

    @Volatile
    private var serverSocket: WebSocket? = null
    private val sockets = CopyOnWriteArrayList<WebSocket>()
    val sentFrames = CopyOnWriteArrayList<String>()
    private val _frames = MutableSharedFlow<String>(replay = Int.MAX_VALUE)
    val frames: Flow<String> = _frames.asSharedFlow()
    private val connectedLatch = CountDownLatch(1)

    private val listener =
        object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                serverSocket = webSocket
                sockets += webSocket
                connectedLatch.countDown()
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                text.trimEnd('\r', '\n').split("\r\n").forEach { line ->
                    sentFrames.add(line)
                    _frames.tryEmit(line)
                    handleIrcCommand(webSocket, line)
                }
            }
        }

    val url: String get() = server.url("/").toString().replace("http://", "ws://")

    fun start() {
        enqueueUpgrade()
        server.start()
    }

    fun enqueueUpgrade() {
        server.enqueue(MockResponse.Builder().webSocketUpgrade(listener).build())
    }

    fun awaitConnection(
        timeout: Long = 5,
        unit: TimeUnit = TimeUnit.SECONDS,
    ): Boolean = connectedLatch.await(timeout, unit)

    fun sendToClient(ircLine: String) {
        // The client can observe the websocket upgrade before onOpen runs on the server side,
        // so wait for the socket instead of silently dropping the frame
        check(awaitConnection()) { "Client did not connect within timeout" }
        checkNotNull(serverSocket).send("$ircLine\r\n")
    }

    fun closeClientConnection() {
        check(awaitConnection()) { "Client did not connect within timeout" }
        checkNotNull(serverSocket).close(1000, null)
    }

    override fun close() {
        sockets.forEach { runCatching { it.close(1000, null) } }
        server.close()
    }

    private fun handleIrcCommand(
        webSocket: WebSocket,
        line: String,
    ) {
        when {
            line.startsWith("NICK ") -> {
                val nick = line.removePrefix("NICK ")
                sendMotd(webSocket, nick)
            }

            line.startsWith("JOIN ") -> {
                val channels = line.removePrefix("JOIN ").split(",")
                channels.forEach { channel ->
                    val ch = channel.trim()
                    webSocket.send(":$NICK!$NICK@$NICK.tmi.twitch.tv JOIN $ch\r\n")
                    webSocket.send(":tmi.twitch.tv 353 $NICK = $ch :$NICK\r\n")
                    webSocket.send(":tmi.twitch.tv 366 $NICK $ch :End of /NAMES list\r\n")
                }
            }

            line.startsWith("PING") && respondToPing -> {
                webSocket.send(":tmi.twitch.tv PONG tmi.twitch.tv\r\n")
            }
        }
    }

    private fun sendMotd(
        webSocket: WebSocket,
        nick: String,
    ) {
        webSocket.send(":tmi.twitch.tv 001 $nick :Welcome, GLHF!\r\n")
        webSocket.send(":tmi.twitch.tv 002 $nick :Your host is tmi.twitch.tv\r\n")
        webSocket.send(":tmi.twitch.tv 003 $nick :This server is rather new\r\n")
        webSocket.send(":tmi.twitch.tv 004 $nick :-\r\n")
        webSocket.send(":tmi.twitch.tv 375 $nick :-\r\n")
        webSocket.send(":tmi.twitch.tv 372 $nick :You are in a maze of twisty passages.\r\n")
        webSocket.send(":tmi.twitch.tv 376 $nick :>\r\n")
    }

    private companion object {
        const val NICK = "justinfan12781923"
    }
}
