package com.flxrs.dankchat.utils.extensions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.emote.EmoteType
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.ui.chat.emotemenu.EmoteItem
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.serialization.json.Json

fun List<GenericEmote>?.toEmoteItems(): List<EmoteItem> = this
    ?.groupBy { it.emoteType.title }
    ?.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    ?.mapValues { (title, emotes) -> EmoteItem.Header(title) + emotes.distinctBy(GenericEmote::id).map(EmoteItem::Emote).sorted() }
    ?.flatMap { it.value }
    .orEmpty()

fun List<GenericEmote>?.toEmoteItemsWithFront(channel: UserName?): List<EmoteItem> {
    if (this == null) return emptyList()
    val grouped = groupBy { it.emoteType.title }
    val frontKey = grouped.keys.find { it.equals(channel?.value, ignoreCase = true) }
    val sorted = grouped.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val ordered =
        if (frontKey != null) {
            val frontEntry = sorted.remove(frontKey)
            buildMap {
                if (frontEntry != null) put(frontKey, frontEntry)
                putAll(sorted)
            }
        } else {
            sorted
        }
    return ordered
        .mapValues { (title, emotes) -> EmoteItem.Header(title) + emotes.distinctBy(GenericEmote::id).map(EmoteItem::Emote).sorted() }
        .flatMap { it.value }
}

// Recent emotes stay in usage order
fun List<GenericEmote>.toRecentEmoteItems(): List<EmoteItem> = when {
    isEmpty() -> emptyList()
    else -> EmoteItem.Header(EmoteType.RecentUsageEmote.title) + distinctBy(GenericEmote::id).map(EmoteItem::Emote)
}

inline fun <V> measureTimeValue(block: () -> V): Pair<V, Long> {
    val start = System.currentTimeMillis()
    return block() to System.currentTimeMillis() - start
}

inline fun <V> measureTimeAndLog(
    logger: KLogger,
    toLoad: String,
    block: () -> V,
): V {
    val (result, time) = measureTimeValue(block)
    when {
        result != null -> logger.info { "Loaded $toLoad in $time ms" }
        else -> logger.info { "Failed to load $toLoad ($time ms)" }
    }

    return result
}

inline fun <reified T> Json.decodeOrNull(json: String): T? = runCatching {
    decodeFromString<T>(json)
}.getOrNull()

val isAtLeastTiramisu: Boolean by lazy { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }

fun Context.hasPermission(permission: String): Boolean = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
