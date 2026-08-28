package com.flxrs.dankchat.ui.chat

import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.preferences.chat.MessageTapAction
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class MessageTapHandlerTest {
    @Test
    fun `each configured action invokes its matching operation`() {
        var invoked: MessageTapAction? = null
        val operations =
            MessageTapOperations(
                reply = { invoked = MessageTapAction.Reply },
                mention = { invoked = MessageTapAction.Mention },
                whisper = { invoked = MessageTapAction.Whisper },
                openUserCard = { invoked = MessageTapAction.OpenUserCard },
                openMessageOptions = { invoked = MessageTapAction.OpenMessageOptions },
                copyMessage = { invoked = MessageTapAction.CopyMessage },
                copyFullMessage = { invoked = MessageTapAction.CopyFullMessage },
            )

        MessageTapAction.entries.drop(1).forEach { action ->
            invoked = null
            messageTapHandler(action, isLoggedIn = true, operations)?.invoke(message)
            assertEquals(action, invoked)
        }
    }

    @Test
    fun `do nothing and unavailable account actions have no handler`() {
        assertNull(messageTapHandler(MessageTapAction.DoNothing, isLoggedIn = true, operations))
        assertNull(messageTapHandler(MessageTapAction.Reply, isLoggedIn = false, operations))
        assertNull(messageTapHandler(MessageTapAction.Mention, isLoggedIn = false, operations))
        assertNull(messageTapHandler(MessageTapAction.Whisper, isLoggedIn = false, operations))
        assertNotNull(messageTapHandler(MessageTapAction.CopyMessage, isLoggedIn = false, operations))
    }

    private val message =
        MessageTapContext(
            messageId = "message-id",
            channel = "channel".toUserName(),
            userId = null,
            userName = "user".toUserName(),
            displayName = "User".toDisplayName(),
            badges = persistentListOf(),
            message = "message",
            fullMessage = "user: message",
            isWhisper = false,
        )

    private val operations =
        MessageTapOperations(
            reply = {},
            mention = {},
            whisper = {},
            openUserCard = {},
            openMessageOptions = {},
            copyMessage = {},
            copyFullMessage = {},
        )
}
