package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.UserName

sealed interface ChatLoadingStep {
    data class RecentMessages(val channel: UserName) : ChatLoadingStep
}

fun List<ChatLoadingStep>.toMergedStrings(): List<String> {
    val recentMessages = filterIsInstance<ChatLoadingStep.RecentMessages>()

    return buildList {
        if (recentMessages.isNotEmpty()) {
            add("RecentMessages(${recentMessages.joinToString(separator = ",") { it.channel.value }})")
        }
    }
}
