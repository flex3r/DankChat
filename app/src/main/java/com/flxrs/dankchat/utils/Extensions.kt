package com.flxrs.dankchat.utils

import com.flxrs.dankchat.chat.ChatItem
import kotlinx.coroutines.*

fun CoroutineScope.timer(interval: Long, action: suspend TimerScope.() -> Unit): Job {
	return launch {
		val scope = TimerScope()

		while (true) {
			try {
				action(scope)
			} catch (ex: Exception) {
				ex.printStackTrace()
			}

			if (scope.isCanceled) {
				break
			}

			delay(interval)
			yield()
		}
	}
}

class TimerScope {
	var isCanceled: Boolean = false
		private set

	fun cancel() {
		isCanceled = true
	}
}

fun List<ChatItem>.replaceWithTimeOuts(name: String): MutableList<ChatItem> = toMutableList().apply {
	val iterate = this.listIterator()
	if (name.isBlank()) {
		while (iterate.hasNext()) {
			val item = iterate.next()
			if (!item.message.isSystem) {
				item.message.timedOut = true
				iterate.set(item)
			}
		}
	} else {
		while (iterate.hasNext()) {
			val item = iterate.next()
			if (!item.message.isSystem && item.message.name == name) {
				item.message.timedOut = true
				iterate.set(item)
			}
		}
	}
	return this
}

fun List<ChatItem>.addAndLimit(item: ChatItem): MutableList<ChatItem> = toMutableList().apply {
	if (size > 999) removeAt(0)
	add(item)
}