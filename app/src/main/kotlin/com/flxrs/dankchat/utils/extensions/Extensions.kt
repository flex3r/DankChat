package com.flxrs.dankchat.utils.extensions

import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.SavedStateHandle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.chat.menu.EmoteItem
import com.flxrs.dankchat.preferences.multientry.MultiEntryItem
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.service.twitch.message.Mention
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

fun List<GenericEmote>?.toEmoteItems(): List<EmoteItem> = this
    ?.groupBy { it.emoteType.title }
    ?.mapValues { (title, emotes) -> EmoteItem.Header(title) + emotes.map(EmoteItem::Emote).sorted() }
    ?.flatMap { it.value }
    .orEmpty()

fun List<GenericEmote>.moveToFront(channel: String?): List<GenericEmote> = this
    .partition { it.emoteType.title.equals(channel, ignoreCase = true) }
    .run { first + second }

fun List<MultiEntryItem.Entry>.mapToMention(): List<Mention> = mapNotNull {
    when {
        it.isRegex -> runCatching { Mention.RegexPhrase(it.entry.toRegex(RegexOption.IGNORE_CASE), it.matchUser) }.getOrNull()
        else       -> Mention.Phrase(it.entry, it.matchUser)
    }
}

fun Set<String>.mapToMention(adapter: JsonAdapter<MultiEntryItem.Entry>?): List<Mention> = mapNotNull {
    runCatching { adapter?.fromJson(it) }.getOrNull()
}.mapToMention()

inline fun <V> measureTimeValue(block: () -> V): Pair<V, Long> {
    val start = System.currentTimeMillis()
    return block() to System.currentTimeMillis() - start
}

inline fun <V> measureTimeAndLog(tag: String, toLoad: String, block: () -> V): V {
    val (result, time) = measureTimeValue(block)
    when {
        result != null -> Log.i(tag, "Loaded $toLoad in $time ms")
        else           -> Log.i(tag, "Failed to load $toLoad ($time ms)")
    }

    return result
}

val Int.isEven get() = (this % 2 == 0)

fun Context.getDrawableAndSetSurfaceTint(@DrawableRes id: Int) = ContextCompat.getDrawable(this, id)?.apply {
    DrawableCompat.setTint(this, ContextCompat.getColor(this@getDrawableAndSetSurfaceTint, R.color.md_theme_onSurface))
}

inline fun <reified T> SavedStateHandle.withData(key: String, block: (T) -> Unit) {
    val data = remove<T>(key) ?: return
    block(data)
}

inline fun <T> T.doIf(predicate: Boolean, action: T.() -> T): T {
    return if (predicate) {
        action()
    } else {
        return this
    }
}