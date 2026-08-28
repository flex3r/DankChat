package com.flxrs.dankchat.ui.chat

import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ChatMessageMapperTest {
    @Test
    fun `sent whisper actions target recipient`() {
        val target = whisper().resolveWhisperReplyTarget("sender".toUserName())

        assertEquals("recipient".toUserName(), target.userName)
        assertEquals("recipient-id".toUserId(), target.userId)
        assertEquals("Recipient".toDisplayName(), target.displayName)
    }

    @Test
    fun `received whisper actions target sender`() {
        val target = whisper().resolveWhisperReplyTarget("recipient".toUserName())

        assertEquals("sender".toUserName(), target.userName)
        assertEquals("sender-id".toUserId(), target.userId)
        assertEquals("Sender".toDisplayName(), target.displayName)
    }

    private fun whisper() = WhisperMessage(
        userId = "sender-id".toUserId(),
        name = "sender".toUserName(),
        displayName = "Sender".toDisplayName(),
        recipientId = "recipient-id".toUserId(),
        recipientName = "recipient".toUserName(),
        recipientDisplayName = "Recipient".toDisplayName(),
        message = "message",
        rawEmotes = "",
        rawBadges = "",
    )
}
