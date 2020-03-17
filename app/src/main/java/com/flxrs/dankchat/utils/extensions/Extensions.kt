package com.flxrs.dankchat.utils.extensions

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.chat.menu.EmoteItem
import com.flxrs.dankchat.preferences.multientry.MultiEntryItem
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.service.twitch.message.Mention
import com.squareup.moshi.JsonAdapter
import java.util.regex.Pattern

fun List<ChatItem>.replaceWithTimeOuts(name: String): List<ChatItem> = apply {
    forEach { item ->
        if (!item.message.isNotify
            && (name.isBlank() || item.message.name.equals(name, true))
        ) {
            item.message.timedOut = true
        }
    }
}

fun List<ChatItem>.replaceWithTimeOut(id: String): List<ChatItem> = apply {
    forEach {
        if (it.message.id == id) {
            it.message.timedOut = true
            return@apply
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

fun <T> MutableMap<String, MutableLiveData<T>>.getAndSet(
    key: String,
    item: T? = null
): MutableLiveData<T> = getOrPut(key) { item?.let { MutableLiveData(item) } ?: MutableLiveData() }

private val emojiCodePoints = listOf(
    IntRange(0x00A9, 0x00A9),
    IntRange(0x00AE, 0x00AE),
    IntRange(0x203C, 0x203C),
    IntRange(0x2049, 0x2049),
    IntRange(0x20E3, 0x20E3),
    IntRange(0x2122, 0x2122),
    IntRange(0x2139, 0x2139),
    IntRange(0x2194, 0x2199),
    IntRange(0x21A9, 0x21AA),
    IntRange(0x231A, 0x231A),
    IntRange(0x231B, 0x231B),
    IntRange(0x2328, 0x2328),
    IntRange(0x23CF, 0x23CF),
    IntRange(0x23E9, 0x23F3),
    IntRange(0x23F8, 0x23FA),
    IntRange(0x24C2, 0x24C2),
    IntRange(0x25AA, 0x25AA),
    IntRange(0x25AB, 0x25AB),
    IntRange(0x25B6, 0x25B6),
    IntRange(0x25C0, 0x25C0),
    IntRange(0x25FB, 0x25FE),
    IntRange(0x2600, 0x27EF),
    IntRange(0x2934, 0x2934),
    IntRange(0x2935, 0x2935),
    IntRange(0x2B00, 0x2BFF),
    IntRange(0x3030, 0x3030),
    IntRange(0x303D, 0x303D),
    IntRange(0x3297, 0x3297),
    IntRange(0x3299, 0x3299),
    IntRange(0x1F000, 0x1F02F),
    IntRange(0x1F0A0, 0x1F0FF),
    IntRange(0x1F100, 0x1F64F),
    IntRange(0x1F680, 0x1F6FF),
    IntRange(0x1F910, 0x1F96B),
    IntRange(0x1F980, 0x1F9E0)
)

private fun Int.isEmoji(): Boolean = emojiCodePoints.any { range -> this in range }

// Adds extra space after every emoji group to support 3rd party emotes directly after emojis
// @badge-info=;badges=broadcaster/1,bits-charity/1;color=#00BCD4;display-name=flex3rs;emotes=521050:9-15,25-31;flags=;id=08649ff3-8fee-4200-8e06-c46bcdfb06e8;mod=0;room-id=73697410;subscriber=0;tmi-sent-ts=1575196101040;turbo=0;user-id=73697410;user-type= :flex3rs!flex3rs@flex3rs.tmi.twitch.tv PRIVMSG #flex3rs :🍓🍑🍊🍋🍍NaM forsenE 🍐🍏🐬🐳NaM forsenE
fun String.appendSpacesAfterEmojiGroup(): Pair<String, List<Int>> {
    val fixedContentBuilder = StringBuilder()
    var previousEmoji = false
    val spaces = mutableListOf<Int>()
    var charCount = 0
    codePoints {
        charCount += Character.charCount(it)
        if (it.isEmoji()) {
            previousEmoji = true
        } else if (previousEmoji) {
            previousEmoji = false
            if (!Character.isWhitespace(it)) {
                fixedContentBuilder.append(" ")
                spaces.add(charCount)
            }
        }
        fixedContentBuilder.appendCodePoint(it)
    }

    return fixedContentBuilder.toString() to spaces
}

inline fun String.codePoints(crossinline block: (Int) -> Unit) {
    var i = 0
    while (i < length) {
        val c1: Char = get(i++)
        if (!Character.isHighSurrogate(c1) || i >= length) {
            block(c1.toInt())
        } else {
            val c2: Char = get(i)
            if (Character.isLowSurrogate(c2)) {
                i++
                block(Character.toCodePoint(c1, c2))
            } else {
                block(c1.toInt())
            }
        }
    }
}

fun List<GenericEmote>?.toEmoteItems(): List<EmoteItem> {
    return this?.groupBy { it.emoteType.title }
        ?.mapValues {
            val title = it.value.first().emoteType.title
            listOf(EmoteItem.Header(title))
                .plus(it.value.map { e -> EmoteItem.Emote(e) })
        }?.flatMap { it.value } ?: listOf()
}

fun List<MultiEntryItem.Entry?>.mapToMention(): List<Mention> {
    return mapNotNull { entry ->
        entry?.let {
            if (it.isRegex) {
                try {
                    Mention.RegexPhrase(it.entry.toPattern(Pattern.CASE_INSENSITIVE).toRegex())
                } catch (t: Throwable) {
                    null
                }
            } else {
                Mention.Phrase(it.entry)
            }
        }

    }
}

fun Set<String>?.mapToMention(adapter: JsonAdapter<MultiEntryItem.Entry>?): List<Mention> {
    return this?.mapNotNull { adapter?.fromJson(it) }?.mapToMention().orEmpty()
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

fun AppCompatActivity.keepScreenOn(keep: Boolean) {
    if (keep) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Suppress("DEPRECATION") // Deprecated for third party Services.
fun <T> Context.isServiceRunning(service: Class<T>) =
    (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(Integer.MAX_VALUE)
        .any { it.service.className == service.name }