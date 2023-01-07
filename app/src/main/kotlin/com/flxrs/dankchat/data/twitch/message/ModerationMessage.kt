package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.connection.dto.ModerationActionData
import com.flxrs.dankchat.data.twitch.connection.dto.ModerationActionType
import com.flxrs.dankchat.utils.DateTimeUtils
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class ModerationMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: Set<Highlight> = emptySet(),
    val channel: UserName,
    val action: Action,
    val creatorUser: UserName? = null,
    val targetUser: UserName? = null,
    val targetMsgId: String? = null,
    val durationSeconds: Int? = null,
    val duration: String? = null,
    val reason: String? = null,
    val fromPubsub: Boolean = false,
    val stackCount: Int = 0,
) : Message() {
    enum class Action {
        Timeout,
        Untimeout,
        Ban,
        Unban,
        Mod,
        Unmod,
        Clear,
        Delete,
    }

    private val durationOrBlank get() = duration?.let { " for $it" }.orEmpty()
    private val reasonOrBlank get() = reason.takeUnless { it.isNullOrBlank() }?.let { ": \"$reason\"" }.orEmpty()
    private fun getTrimmedReasonOrBlank(showDeletedMessage: Boolean): String {
        if (!showDeletedMessage) return ""

        val fullReason = reason.orEmpty()
        val trimmed = when {
            fullReason.length > 50 -> "${fullReason.take(50)}…"
            else                   -> fullReason
        }
        return " saying: \"$trimmed\""
    }

    private val creatorOrBlank get() = creatorUser?.let { " by $it" }.orEmpty()
    private val countOrBlank
        get() = when {
            stackCount > 1 -> " ($stackCount times)"
            else           -> ""
        }

    // TODO localize
    fun getSystemMessage(currentUser: UserName?, showDeletedMessage: Boolean): String {
        return when (action) {
            Action.Timeout   -> when (targetUser) {
                currentUser -> "You were timed out$durationOrBlank$creatorOrBlank$reasonOrBlank.$countOrBlank"
                else        -> when (creatorUser) {
                    null -> "$targetUser has been timed out$durationOrBlank.$countOrBlank" // irc
                    else -> "$creatorUser timed out $targetUser$durationOrBlank.$countOrBlank"
                }
            }

            Action.Untimeout -> "$creatorUser untimedout $targetUser."
            Action.Ban       -> when (targetUser) {
                currentUser -> "You were banned$creatorOrBlank$reasonOrBlank."
                else        -> when (creatorUser) {
                    null -> "$targetUser has been permanently banned." // irc
                    else -> "$creatorUser banned $targetUser$reasonOrBlank."

                }
            }

            Action.Unban     -> "$creatorUser unbanned $targetUser."
            Action.Mod       -> "$creatorUser modded $targetUser."
            Action.Unmod     -> "$creatorUser unmodded $targetUser."
            Action.Delete    -> when (creatorUser) {
                null -> "A message from $targetUser was deleted${getTrimmedReasonOrBlank(showDeletedMessage)}."
                else -> "$creatorUser deleted message from $targetUser${getTrimmedReasonOrBlank(showDeletedMessage)}."
            }

            Action.Clear     -> when (creatorUser) {
                null -> "Chat has been cleared by a moderator."
                else -> "$creatorUser cleared the chat."
            }
        }
    }

    val canClearMessages: Boolean
        get() = action == Action.Clear || action == Action.Ban || action == Action.Timeout

    companion object {
        fun parseClearChat(message: IrcMessage): ModerationMessage = with(message) {
            val channel = params[0].substring(1)
            val target = params.getOrNull(1)
            val durationSeconds = tags["ban-duration"]?.toIntOrNull()
            val duration = durationSeconds?.let { DateTimeUtils.formatSeconds(it) }
            val ts = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
            val id = tags["id"] ?: UUID.randomUUID().toString()
            val action = when {
                target == null          -> Action.Clear
                durationSeconds == null -> Action.Ban
                else                    -> Action.Timeout
            }

            return ModerationMessage(
                timestamp = ts,
                id = id,
                channel = channel.toUserName(),
                action = action,
                targetUser = target?.toUserName(),
                durationSeconds = durationSeconds,
                duration = duration,
                stackCount = if (target != null && duration != null) 1 else 0,
                fromPubsub = false,
            )
        }

        fun parseClearMessage(message: IrcMessage): ModerationMessage = with(message) {
            val channel = params[0].substring(1)
            val target = tags["login"]
            val targetMsgId = tags["target-msg-id"]
            val reason = params.getOrNull(1)
            val ts = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
            val id = tags["id"] ?: UUID.randomUUID().toString()

            return ModerationMessage(
                timestamp = ts,
                id = id,
                channel = channel.toUserName(),
                action = Action.Delete,
                targetUser = target?.toUserName(),
                targetMsgId = targetMsgId,
                reason = reason,
                fromPubsub = false,
            )
        }

        fun parseModerationAction(timestamp: Instant, channel: UserName, data: ModerationActionData): ModerationMessage {
            val seconds = data.args?.getOrNull(1)?.toIntOrNull()
            val duration = parseDuration(seconds, data)
            val targetUser = parseTargetUser(data)
            val targetMsgId = parseTargetMsgId(data)
            val reason = parseReason(data)

            return ModerationMessage(
                timestamp = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                id = data.msgId ?: UUID.randomUUID().toString(),
                channel = channel,
                action = data.moderationAction.toAction(),
                creatorUser = data.creator,
                targetUser = targetUser,
                targetMsgId = targetMsgId,
                durationSeconds = seconds,
                duration = duration,
                reason = reason,
                stackCount = if (data.targetUserName != null && duration != null) 1 else 0,
                fromPubsub = true,
            )
        }

        private fun parseDuration(seconds: Int?, data: ModerationActionData): String? = when (data.moderationAction) {
            ModerationActionType.Timeout -> seconds?.let { DateTimeUtils.formatSeconds(seconds) }
            else                         -> null
        }

        private fun parseReason(data: ModerationActionData): String? = when (data.moderationAction) {
            ModerationActionType.Ban,
            ModerationActionType.Delete  -> data.args?.getOrNull(1)

            ModerationActionType.Timeout -> data.args?.getOrNull(2)
            else                         -> null
        }

        private fun parseTargetUser(data: ModerationActionData): UserName? = when (data.moderationAction) {
            ModerationActionType.Delete -> data.args?.getOrNull(0)?.toUserName()
            else                        -> data.targetUserName
        }

        private fun parseTargetMsgId(data: ModerationActionData): String? = when (data.moderationAction) {
            ModerationActionType.Delete -> data.args?.getOrNull(2)
            else                        -> null
        }

        private fun ModerationActionType.toAction() = when (this) {
            ModerationActionType.Timeout   -> Action.Timeout
            ModerationActionType.Untimeout -> Action.Untimeout
            ModerationActionType.Ban       -> Action.Ban
            ModerationActionType.Unban     -> Action.Unban
            ModerationActionType.Mod       -> Action.Mod
            ModerationActionType.Unmod     -> Action.Unmod
            ModerationActionType.Clear     -> Action.Clear
            ModerationActionType.Delete    -> Action.Delete
        }
    }
}
