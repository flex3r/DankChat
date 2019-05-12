package com.flxrs.dankchat.service.irc

import android.util.Log
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

class IrcListener(private val onMessage: (IrcMessage) -> Unit, private val onError: () -> Unit) {
	private val listening = AtomicBoolean(false)

	fun start(stream: InputStream) {
		listening.compareAndSet(false, true)
		try {
			val reader = stream.bufferedReader()

			while (listening.get()) {
				val line = reader.readLine()
				if (line == null) {
					Log.d(TAG, "line null")
					return
				}
				onMessage(IrcMessage.parse(line))
			}
		} catch (e: Exception) {
			Log.e(TAG, Log.getStackTraceString(e))
			onError()
		}
	}

	fun stop() {
		listening.compareAndSet(true, false)
	}

	companion object {
		private val TAG = IrcWriter::class.java.simpleName
	}
}