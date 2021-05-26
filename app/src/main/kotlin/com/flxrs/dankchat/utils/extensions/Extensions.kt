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
import java.util.regex.Pattern

fun List<GenericEmote>?.toEmoteItems(): List<EmoteItem> {
    return this?.groupBy { it.emoteType.title }
        ?.mapValues { (title, emotes) ->
            listOf(EmoteItem.Header(title)) + emotes.map(EmoteItem::Emote).sorted()
        }?.flatMap { it.value } ?: listOf()
}

fun List<GenericEmote>?.moveToFront(channel: String?): List<GenericEmote>? = this?.partition { it.emoteType.title.equals(channel, ignoreCase = true) }.let { it?.first?.plus(it.second) }

fun List<MultiEntryItem.Entry?>.mapToMention(): List<Mention> = mapNotNull { entry ->
    entry?.let {
        when {
            it.isRegex -> try {
                Mention.RegexPhrase(it.entry.toPattern(Pattern.CASE_INSENSITIVE).toRegex(), it.matchUser)
            } catch (t: Throwable) {
                null
            }
            else -> Mention.Phrase(it.entry, it.matchUser)
        }
    }

}

fun Set<String>?.mapToMention(adapter: JsonAdapter<MultiEntryItem.Entry>?): List<Mention> {
    return this?.mapNotNull { adapter?.fromJson(it) }?.mapToMention().orEmpty()
}

inline fun <V> measureTimeValue(block: () -> V): Pair<V, Long> {
    val start = System.currentTimeMillis()
    return block() to System.currentTimeMillis() - start
}

inline fun <V> measureTimeAndLog(tag: String, toLoad: String, block: () -> V): V {
    val (result, time) = measureTimeValue(block)
    if (result != null) {
        Log.i(tag, "Loaded $toLoad in $time ms")
    } else {
        Log.i(tag, "Failed to load $toLoad ($time ms")
    }
    return result
}

val Int.isEven
    get() = (this % 2 == 0)

fun Context.getDrawableAndSetSurfaceTint(@DrawableRes id: Int) = getDrawable(id)?.apply {
    DrawableCompat.setTint(this, ContextCompat.getColor(this@getDrawableAndSetSurfaceTint, R.color.color_on_surface))
}

inline fun <reified T> SavedStateHandle.withData(key: String, block: (T) -> Unit) {
    val data = remove<T>(key) ?: return
    block(data)
}