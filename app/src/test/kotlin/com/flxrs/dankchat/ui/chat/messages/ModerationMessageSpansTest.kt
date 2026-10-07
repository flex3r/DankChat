package com.flxrs.dankchat.ui.chat.messages

import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.ui.chat.ChatMessageUiState
import com.flxrs.dankchat.ui.chat.messages.ModerationSpan.Role
import com.flxrs.dankchat.utils.TextResource
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ModerationMessageSpansTest {
    private val templates =
        mapOf(
            R.string.mod_delete_by_creator_message to "%1\$s deleted message from %2\$s saying: %3\$s",
            R.string.mod_timeout_by_creator to "%1\$s timed out %2\$s for %3\$s",
            R.string.mod_message_with_count to "%1\$s%2\$s",
        )
    private val plurals =
        mapOf(
            R.plurals.duration_seconds to "%1\$d seconds",
            R.plurals.mod_count_suffix to " (%1\$d times)",
        )

    private val resources =
        mockk<Resources> {
            every { getString(any(), *anyVararg()) } answers { templates.getValue(firstArg()).format(*formatArgs(args.drop(1))) }
            every { getQuantityString(any(), any(), *anyVararg()) } answers { plurals.getValue(firstArg()).format(*formatArgs(args.drop(2))) }
        }

    @Test
    fun `deleted message matching template text is highlighted at its own position`() {
        val resolved = moderationUi(delete(creator = "flex3rs", target = "forsen", message = "message")).resolveWithSpans(resources)

        assertEquals("flex3rs deleted message from forsen saying: message", resolved.text)
        assertEquals(listOf("flex3rs" to Role.Creator, "forsen" to Role.Target, "message" to Role.Argument), resolved.highlighted())
        assertEquals(resolved.text.lastIndexOf("message"), resolved.spans.last().start)
    }

    @Test
    fun `deleted message equal to the names keeps each role`() {
        val resolved = moderationUi(delete(creator = "flex3rs", target = "flex3rs", message = "flex3rs")).resolveWithSpans(resources)

        assertEquals(listOf(Role.Creator, Role.Target, Role.Argument), resolved.spans.map { it.role })
        assertEquals(listOf(0, 29, 45), resolved.spans.map { it.start })
    }

    @Test
    fun `stacked timeout keeps spans inside the count wrapper`() {
        val duration = TextResource.PluralRes(R.plurals.duration_seconds, 10, persistentListOf(10))
        val timeout =
            ModerationMessage(
                timestamp = 0,
                id = "timeout",
                channel = UserName("forsen"),
                action = ModerationMessage.Action.Timeout(duration),
                creatorUserDisplay = DisplayName("flex3rs"),
                targetUser = UserName("seconds"),
                targetUserDisplay = DisplayName("seconds"),
                stackCount = 2,
            )

        val resolved = moderationUi(timeout, arguments = persistentListOf(duration)).resolveWithSpans(resources)

        assertEquals("flex3rs timed out seconds for 10 seconds (2 times)", resolved.text)
        assertEquals(listOf("flex3rs" to Role.Creator, "seconds" to Role.Target, "10 seconds" to Role.Argument), resolved.highlighted())
    }

    private fun ResolvedModerationText.highlighted() = spans.map { text.substring(it.start, it.start + it.length) to it.role }

    // mockk may pass the varargs as one array
    private fun formatArgs(args: List<Any?>): Array<Any?> = args
        .flatMap { arg ->
            when (arg) {
                is Array<*> -> arg.toList()
                else -> listOf(arg)
            }
        }.toTypedArray()

    private fun delete(
        creator: String,
        target: String,
        message: String,
    ) = ModerationMessage(
        timestamp = 0,
        id = "delete",
        channel = UserName("forsen"),
        action = ModerationMessage.Action.Delete,
        creatorUserDisplay = DisplayName(creator),
        targetUserDisplay = DisplayName(target),
        reason = message,
    )

    private fun moderationUi(
        moderation: ModerationMessage,
        arguments: ImmutableList<Any> = persistentListOf(moderation.reason.orEmpty()),
    ) = ChatMessageUiState.ModerationMessageUi(
        id = moderation.id,
        tag = 0,
        timestamp = "",
        lightBackgroundColor = Color.Transparent,
        darkBackgroundColor = Color.Transparent,
        textAlpha = 1f,
        channel = moderation.channel,
        message = moderation.getSystemMessage(currentUser = null, showDeletedMessage = true),
        creatorName = moderation.creatorUserDisplay?.toString(),
        targetName = moderation.targetUserDisplay?.toString(),
        arguments = arguments,
    )
}
