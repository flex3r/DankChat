package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.UserName

sealed interface ChatLoadingStep {
    data class Chatters(val channel: UserName) : ChatLoadingStep
    data class RecentMessages(val channel: UserName) : ChatLoadingStep
}

fun List<ChatLoadingStep>.toMergedStrings(): List<String> {
    val chatters = filterIsInstance<ChatLoadingStep.Chatters>()
    val recentMessages = filterIsInstance<ChatLoadingStep.RecentMessages>()

    return buildList {
        if (chatters.isNotEmpty()) {
            add("Chatters(${chatters.joinToString(separator = ",") { it.channel.value }})")
        }
        if (recentMessages.isNotEmpty()) {
            add("RecentMessages(${recentMessages.joinToString(separator = ",") { it.channel.value }})")
        }
    }
}
