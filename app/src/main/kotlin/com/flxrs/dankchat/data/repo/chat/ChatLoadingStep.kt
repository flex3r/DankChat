package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.UserName

sealed class ChatLoadingStep {
    data class Chatters(val channel: UserName) : ChatLoadingStep()
    data class RecentMessages(val channel: UserName) : ChatLoadingStep()
}