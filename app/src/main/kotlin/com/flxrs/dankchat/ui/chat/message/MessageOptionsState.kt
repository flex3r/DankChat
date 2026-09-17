package com.flxrs.dankchat.ui.chat.message

import androidx.compose.runtime.Immutable
import com.flxrs.dankchat.data.UserName
import kotlinx.collections.immutable.ImmutableList

@Immutable
sealed interface MessageOptionsState {
    data object Loading : MessageOptionsState

    data object NotFound : MessageOptionsState

    sealed interface Found : MessageOptionsState {
        val name: UserName
        val originalMessage: String
        val canModerate: Boolean
        val urls: ImmutableList<String>

        data class RegularMessage(
            override val name: UserName,
            override val originalMessage: String,
            override val canModerate: Boolean,
            override val urls: ImmutableList<String>,
            val messageId: String,
            val rootThreadId: String,
            val rootThreadName: UserName?,
            val rootThreadMessage: String?,
            val replyName: UserName,
            val hasReplyThread: Boolean,
            val replyAction: MessageReplyAction?,
        ) : Found

        data class AutomodMessage(
            override val name: UserName,
            override val originalMessage: String,
            override val canModerate: Boolean,
            override val urls: ImmutableList<String>,
        ) : Found
    }
}
