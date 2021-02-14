package com.flxrs.dankchat.utils.extensions

import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.service.twitch.message.TwitchMessage

fun List<ChatItem>.replaceWithTimeOuts(name: String): List<ChatItem> = apply {
    forEach { (message) ->
        if (message is TwitchMessage && !message.isNotify
            && (name.isBlank() || message.name.equals(name, true))
        ) {
            message.timedOut = true
        }
    }
}

fun List<ChatItem>.replaceWithTimeOut(id: String): List<ChatItem> = apply {
    forEach {
        if (it.message is TwitchMessage && it.message.id == id) {
            it.message.timedOut = true
            return@apply
        }
    }
}

fun List<ChatItem>.addAndLimit(item: ChatItem, scrollBackLength: Int): MutableList<ChatItem> = toMutableList().apply {
    add(item)
    if (size > scrollBackLength) removeAt(0)
}

fun List<ChatItem>.addAndLimit(
    collection: Collection<ChatItem>,
    scrollBackLength: Int,
    checkForDuplications: Boolean = false
): MutableList<ChatItem> = takeLast(scrollBackLength).toMutableList().apply {
    for (item in collection) {
        if (!checkForDuplications || !this.any { it.message.id == item.message.id })
            add(item)
        if (size > scrollBackLength) removeAt(0)
    }
}
