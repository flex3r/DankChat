package com.flxrs.dankchat.service.irc

import android.util.Log
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

class IrcConnection(private val host: String, private val port: Int, private val timeout: Int = 30, private val scope: CoroutineScope,
					onError: () -> Unit, onMessage: (IrcMessage) -> Unit) {

	private var socket: Socket? = null
	private val listener = IrcListener(onMessage, onError)
	private val writer = IrcWriter(onError)

	suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
		return@withContext try {
			socket = Socket()
			socket?.connect(InetSocketAddress(host, port), timeout * 1000)
			val connected = socket?.isConnected ?: false
			if (connected) {
				scope.launch { socket?.getInputStream()?.let { input -> listener.start(input) } }
				scope.launch { socket?.getOutputStream()?.let { output -> writer.start(output) } }
			}
			connected
		} catch (e: Exception) {
			Log.e(TAG, Log.getStackTraceString(e))
			false
		}
	}

	@Synchronized
	fun close() {
		listener.stop()
		writer.stop()
		socket?.close()
		scope.coroutineContext.cancelChildren()
	}

	@Synchronized
	fun write(msg: String) {
		writer.write(msg)
	}

	companion object {
		private val TAG = IrcConnection::class.java.simpleName
	}

}