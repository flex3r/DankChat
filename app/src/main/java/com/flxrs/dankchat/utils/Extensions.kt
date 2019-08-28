package com.flxrs.dankchat.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
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

fun List<ChatItem>.replaceWithTimeOuts(name: String): MutableList<ChatItem> =
    toMutableList().apply {
        forEachIndexed { i, item ->
            if (!item.message.isNotify
                && (name.isBlank() || item.message.name.equals(name, true))
            ) {
                item.message.timedOut = true
                this[i] = item
            }
        }
    }

fun List<ChatItem>.addAndLimit(item: ChatItem): MutableList<ChatItem> = toMutableList().apply {
    add(item)
    if (size > 500) removeAt(0)
}

fun List<ChatItem>.addAndLimit(
    collection: Collection<ChatItem>,
    checkForDuplications: Boolean = false
): MutableList<ChatItem> = toMutableList().apply {
    for (item in collection) {
        if (!checkForDuplications || !this.any { it.message.id == item.message.id })
        add(item)
        if (size > 500) removeAt(0)
    }
}

fun Fragment.hideKeyboard() {
    view?.let { activity?.hideKeyboard(it) }
}

fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

@Suppress("DEPRECATION") // Deprecated for third party Services.
fun <T> Context.isServiceRunning(service: Class<T>) =
    (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(Integer.MAX_VALUE)
        .any { it.service.className == service.name }

// from https://medium.com/@al.e.shevelev/how-to-reduce-scroll-sensitivity-of-viewpager2-widget-87797ad02414
fun ViewPager2.reduceDragSensitivity() {
    val recyclerView =
        ViewPager2::class.java.getDeclaredField("mRecyclerView").apply { isAccessible = true }.get(
            this
        ) as RecyclerView
    RecyclerView::class.java.getDeclaredField("mTouchSlop").apply {
        isAccessible = true
        set(recyclerView, (get(recyclerView) as Int) * 2)
    }
}