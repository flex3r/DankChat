package com.flxrs.dankchat.data.repo.chat

sealed class ChatLoadingStep {
    data class Chatters(val channel: String): ChatLoadingStep()
    data class RecentMessages(val channel: String): ChatLoadingStep()
}