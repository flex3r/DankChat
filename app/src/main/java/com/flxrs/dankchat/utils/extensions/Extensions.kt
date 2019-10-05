package com.flxrs.dankchat.utils.extensions

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.chat.menu.EmoteItem
import com.flxrs.dankchat.service.twitch.emote.GenericEmote

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

private val emojiRegex =
    Regex("\u00A9|\u00AE|[\u2000-\u3300]|[\uD83C\uD000-\uD83C\uDFFF]|[\uD83D\uD000-\uD83D\uDFFF]|[\uD83E\uD000-\uD83E\uDFFF]")

fun Char.isEmoji(): Boolean {
    return emojiRegex.matches("$this")
}

fun List<GenericEmote>?.toEmoteItems(): List<EmoteItem> {
    return this?.groupBy { it.emoteType.title }
        ?.mapValues {
            val title = it.value.first().emoteType.title
            listOf(EmoteItem.Header(title))
                .plus(it.value.map { e -> EmoteItem.Emote(e) })
        }?.flatMap { it.value } ?: listOf()
}

fun Fragment.hideKeyboard() {
    view?.let { activity?.hideKeyboard(it) }
}

fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

fun View.setVisibility(visible: Boolean) {
    visibility = if (visible) {
        View.VISIBLE
    } else {
        View.GONE
    }
}

@Suppress("DEPRECATION") // Deprecated for third party Services.
fun <T> Context.isServiceRunning(service: Class<T>) =
    (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(Integer.MAX_VALUE)
        .any { it.service.className == service.name }