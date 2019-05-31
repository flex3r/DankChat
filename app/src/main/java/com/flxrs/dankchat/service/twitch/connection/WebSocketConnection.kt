package com.flxrs.dankchat.service.twitch.connection

import android.util.Log
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.utils.timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import okhttp3.*

class WebSocketConnection(private val scope: CoroutineScope, private val onMessage: (IrcMessage) -> Unit) {

	private val client = OkHttpClient.Builder()
			.retryOnConnectionFailure(true)
			.build()
	private val request = Request.Builder()
			.url("wss://irc-ws.chat.twitch.tv")
			.build()

	private var nick = ""
	private var oAuth = ""
	var isJustinFan = false

	private val channels = mutableSetOf<String>()

	private lateinit var readerWebSocket: WebSocket
	private lateinit var writerWebSocket: WebSocket

	private var readerConnected = false
	private var readerPong = false

	private var writerConnected = false
	private var writerPong = false

	fun sendMessage(msg: String) {
		if (readerConnected && writerConnected) {
			writerWebSocket.sendMessage(msg)
		}
	}

	fun joinChannel(channel: String) {
		if (readerConnected && writerConnected && !channels.contains(channel)) {
			writerWebSocket.sendMessage("JOIN #$channel")
			readerWebSocket.sendMessage("JOIN #$channel")
		}
		channels.add(channel)
	}

	fun connect(nick: String, oAuth: String, channel: String) {
		this.nick = nick
		this.oAuth = oAuth
		isJustinFan = (oAuth.isBlank() || !oAuth.startsWith("oauth:"))
		channels.add(channel)

		readerWebSocket = client.newWebSocket(request, ReaderWebSocketListener())
		writerWebSocket = client.newWebSocket(request, WriterWebSocketListener())
	}

	private inner class ReaderWebSocketListener : WebSocketListener() {

		override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
			Log.e(TAG, "Reader closed: $reason")
			readerConnected = false
		}

		override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
			Log.e(TAG, "Reader failed: ${Log.getStackTraceString(t)}")
			readerConnected = false
		}

		override fun onMessage(webSocket: WebSocket, text: String) {
			val splits = text.split("\r\n")
			val size = splits.size

			splits.forEachIndexed { i, line ->
				if (i != size - 1) {
					val ircMessage = IrcMessage.parse(line)
					when (ircMessage.command) {
						"PING"      -> webSocket.handlePing()
						"PONG"      -> readerPong = false
						"RECONNECT" -> handleReaderReconnect()
						else        -> onMessage(ircMessage)
					}
				}
			}
		}

		override fun onOpen(webSocket: WebSocket, response: Response) {
			readerConnected = true
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
			Log.e(TAG, "Writer closed: $reason")
			writerConnected = false
		}

		override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
			Log.e(TAG, "Writer failed: ${Log.getStackTraceString(t)}")
			writerConnected = false
		}

		override fun onMessage(webSocket: WebSocket, text: String) {
			val splits = text.split("\r\n")
			val size = splits.size

			splits.forEachIndexed { i, line ->
				if (i != size - 1) {
					val ircMessage = IrcMessage.parse(line)
					when (ircMessage.command) {
						"PING"      -> webSocket.handlePing()
						"PONG"      -> writerPong = false
						"RECONNECT" -> handleWriterReconnect()
						else        -> Unit
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

	fun close() {
		scope.coroutineContext.cancel()
		handleReaderReconnect()
		handleWriterReconnect()
	}

	private fun handleWriterReconnect() {
		writerWebSocket.cancel()
		writerWebSocket = client.newWebSocket(request, WriterWebSocketListener())
	}

	private fun handleReaderReconnect() {
		readerWebSocket.cancel()
		readerWebSocket = client.newWebSocket(request, ReaderWebSocketListener())
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
			close()
			return@timer
		}

		if (readerConnected) {
			readerWebSocket.sendMessage("PING")
			readerPong = true
		}
	}

	private fun setupWriterPingInterval() = scope.timer(5 * 60 * 1000) {
		if (writerPong) {
			cancel()
			close()
			return@timer
		}

		if (writerConnected) {
			writerWebSocket.sendMessage("PING")
			writerPong = true
		}
	}

	companion object {
		private val TAG = WebSocketConnection::class.java.simpleName
	}

}
