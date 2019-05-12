package com.flxrs.dankchat.service.irc

import android.util.Log
import java.io.OutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class IrcWriter(private val onError: () -> Unit) {
	private val messages = ConcurrentLinkedQueue<String>()
	private val listening = AtomicBoolean(false)

	fun start(stream: OutputStream) {
		listening.compareAndSet(false, true)
		try {
			val writer = stream.bufferedWriter()
			while (listening.get()) {
				while (messages.isNotEmpty()) writer.write(messages.poll())
				writer.flush()
			}
		} catch (e: Exception) {
			Log.e(TAG, Log.getStackTraceString(e))
			onError()
		}
	}

	fun stop() {
		listening.compareAndSet(true, false)
	}

	fun write(msg: String) {
		messages.offer("${msg.trimEnd()}\r\n")
	}

	companion object {
		private val TAG = IrcWriter::class.java.simpleName
	}
}