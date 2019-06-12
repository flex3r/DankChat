package com.flxrs.dankchat.utils

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
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
	val iterate = listIterator()
	if (name.isBlank()) {
		while (iterate.hasNext()) {
			val item = iterate.next()
			if (!item.message.isNotify) {
				item.message.timedOut = true
				iterate.set(item)
			}
		}
	} else {
		while (iterate.hasNext()) {
			val item = iterate.next()
			if (!item.message.isNotify && item.message.name.equals(name, true)) {
				item.message.timedOut = true
				iterate.set(item)
			}
		}
	}
}

fun List<ChatItem>.addAndLimit(item: ChatItem): MutableList<ChatItem> = toMutableList().apply {
	add(item)
	if (size > 500) removeAt(0)
}

fun List<ChatItem>.addAndLimit(collection: Collection<ChatItem>, checkForDuplicates: Boolean = false): MutableList<ChatItem> = toMutableList().apply {
	for (item in collection) {
		if (!checkForDuplicates || !this.any { it.message.id == item.message.id }) {
			add(item)
			if (size > 500) removeAt(0)
		}
	}
}

fun Fragment.hideKeyboard() {
	view?.let { activity?.hideKeyboard(it) }
}

fun Context.hideKeyboard(view: View) {
	val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
	inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}