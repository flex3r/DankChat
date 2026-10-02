package com.flxrs.dankchat.utils.extensions

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.chat.ChatImportance
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.utils.TextResource
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal class ModerationOperationsTest {
    private val forsen = UserName("forsen")
    private val pajlada = UserName("pajlada")

    @Test
    fun `timeout marks only the target user's messages in that channel`() {
        val items = listOf(
            item(id = "1", channel = forsen, user = "target"),
            item(id = "2", channel = forsen, user = "other"),
            item(id = "3", channel = pajlada, user = "target"),
        )

        val marked = items.markModeratedMessages(moderation(forsen, ModerationMessage.Action.Timeout(TextResource.Plain("10s")), targetUser = "target"))

        assertEquals(listOf(true, false, false), marked.map { (it.message as PrivMessage).timedOut })
        assertEquals(ChatImportance.DELETED, marked[0].importance)
        assertEquals(items[0].tag + 1, marked[0].tag)
    }

    @Test
    fun `clear marks every message in that channel`() {
        val items = listOf(
            item(id = "1", channel = forsen, user = "a"),
            item(id = "2", channel = forsen, user = "b"),
            item(id = "3", channel = pajlada, user = "a"),
        )

        val marked = items.markModeratedMessages(moderation(forsen, ModerationMessage.Action.Clear))

        assertEquals(listOf(true, true, false), marked.map { (it.message as PrivMessage).timedOut })
    }

    @Test
    fun `delete marks only the target message`() {
        val items = listOf(
            item(id = "1", channel = forsen, user = "a"),
            item(id = "2", channel = forsen, user = "a"),
        )

        val marked = items.markModeratedMessages(moderation(forsen, ModerationMessage.Action.Delete, targetMsgId = "2"))

        assertEquals(listOf(false, true), marked.map { (it.message as PrivMessage).timedOut })
    }

    @Test
    fun `unaffected or already timed out lists are returned unchanged`() {
        val items = listOf(item(id = "1", channel = forsen, user = "a", timedOut = true))

        assertSame(items, items.markModeratedMessages(moderation(forsen, ModerationMessage.Action.Ban, targetUser = "a")))
        assertSame(items, items.markModeratedMessages(moderation(forsen, ModerationMessage.Action.Ban, targetUser = "b")))
    }

    @Test
    fun `history drops a delete that is already shown as its EventSub version`() {
        val shown = listOf(ChatItem(moderation(forsen, ModerationMessage.Action.Delete, targetUser = "a", targetMsgId = "1", id = "eventsub", timestamp = 1_000, fromEventSource = true)))
        val history = listOf(
            ChatItem(moderation(forsen, ModerationMessage.Action.Delete, targetUser = "a", targetMsgId = "1", id = "irc", timestamp = 1_500)),
            ChatItem(moderation(forsen, ModerationMessage.Action.Delete, targetUser = "a", targetMsgId = "2", id = "other", timestamp = 1_500)),
        )

        assertEquals(listOf("other"), history.withoutShownModerationMessages(shown).map { it.message.id })
    }

    @Test
    fun `history keeps moderation of the same user outside the dedup window`() {
        val shown = listOf(ChatItem(moderation(forsen, ModerationMessage.Action.Ban, targetUser = "a", id = "eventsub", timestamp = 1_000, fromEventSource = true)))
        val history = listOf(
            ChatItem(moderation(forsen, ModerationMessage.Action.Ban, targetUser = "a", id = "same", timestamp = 2_000)),
            ChatItem(moderation(forsen, ModerationMessage.Action.Ban, targetUser = "a", id = "later", timestamp = 10_000)),
        )

        assertEquals(listOf("later"), history.withoutShownModerationMessages(shown).map { it.message.id })
    }

    @Test
    fun `live IRC delete after its EventSub version is discarded`() {
        val eventSub = moderation(forsen, ModerationMessage.Action.Delete, targetUser = "a", targetMsgId = "1", id = "eventsub", fromEventSource = true)
        val items = listOf(item(id = "1", channel = forsen, user = "a", timedOut = true), ChatItem(eventSub))

        val irc = moderation(forsen, ModerationMessage.Action.Delete, targetUser = "a", targetMsgId = "1", id = "irc")
        val result = items.replaceWithTimeout(irc, scrollBackLength = 500, onMessageRemoved = {})

        assertEquals(listOf("1", "eventsub"), result.map { it.message.id })
    }

    @Test
    fun `live EventSub delete replaces its IRC version`() {
        val irc = moderation(forsen, ModerationMessage.Action.Delete, targetUser = "a", targetMsgId = "1", id = "irc")
        val items = listOf(item(id = "1", channel = forsen, user = "a", timedOut = true), ChatItem(irc))

        val eventSub = moderation(forsen, ModerationMessage.Action.Delete, targetUser = "a", targetMsgId = "1", id = "eventsub", fromEventSource = true)
        val result = items.replaceWithTimeout(eventSub, scrollBackLength = 500, onMessageRemoved = {})

        assertEquals(listOf("1", "eventsub"), result.map { it.message.id })
    }

    private fun item(
        id: String,
        channel: UserName,
        user: String,
        timedOut: Boolean = false,
    ) = ChatItem(
        PrivMessage(
            id = id,
            channel = channel,
            sourceChannel = null,
            name = UserName(user),
            displayName = DisplayName(user),
            message = "message",
            timedOut = timedOut,
            tags = emptyMap(),
        ),
        isMentionTab = true,
    )

    private fun moderation(
        channel: UserName,
        action: ModerationMessage.Action,
        targetUser: String? = null,
        targetMsgId: String? = null,
        id: String = "moderation",
        timestamp: Long = 0,
        fromEventSource: Boolean = false,
    ) = ModerationMessage(
        timestamp = timestamp,
        id = id,
        channel = channel,
        action = action,
        targetUser = targetUser?.let(::UserName),
        targetMsgId = targetMsgId,
        fromEventSource = fromEventSource,
    )
}
