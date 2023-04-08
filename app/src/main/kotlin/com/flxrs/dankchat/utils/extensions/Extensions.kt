package com.flxrs.dankchat.utils.extensions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.SavedStateHandle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.chat.emotemenu.EmoteItem
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.google.android.material.color.MaterialColors
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

fun List<GenericEmote>?.toEmoteItems(): List<EmoteItem> = this
    ?.groupBy { it.emoteType.title }
    ?.mapValues { (title, emotes) -> EmoteItem.Header(title) + emotes.map(EmoteItem::Emote).sorted() }
    ?.flatMap { it.value }
    .orEmpty()

fun List<GenericEmote>.moveToFront(channel: UserName?): List<GenericEmote> = this
    .partition { it.emoteType.title.equals(channel?.value, ignoreCase = true) }
    .run { first + second }

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

inline fun <reified T> Json.decodeOrNull(json: String): T? = runCatching {
    decodeFromString<T>(json)
}.getOrNull()

val Int.isEven get() = (this % 2 == 0)

fun Context.getDrawableAndSetSurfaceTint(@DrawableRes id: Int) = ContextCompat.getDrawable(this, id)?.apply {
    val color = MaterialColors.getColor(this@getDrawableAndSetSurfaceTint, R.attr.colorOnSurface, "DankChat")
    DrawableCompat.setTint(this, color)
}

inline fun <reified T> SavedStateHandle.withData(key: String, block: (T) -> Unit) {
    val data = remove<T>(key) ?: return
    block(data)
}

val isAtLeastTiramisu: Boolean by lazy { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }

fun Context.hasPermission(permission: String): Boolean = ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
