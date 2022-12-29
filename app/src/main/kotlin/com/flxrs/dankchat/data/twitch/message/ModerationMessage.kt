package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.connection.dto.ModerationActionData
import com.flxrs.dankchat.data.twitch.connection.dto.ModerationActionType
import com.flxrs.dankchat.utils.DateTimeUtils
import java.time.Instant
import java.time.ZoneId
import java.util.*

data class ModerationMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: Set<Highlight> = emptySet(),
    val channel: UserName,
    val action: Action,
    val creatorUser: UserName? = null,
    val targetUser: UserName? = null,
    val durationSeconds: Int? = null,
    val duration: String? = null,
    val reason: String? = null,
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
    private val creatorOrBlank get() = creatorUser?.let { " by $it" }.orEmpty()
    private val countOrBlank
        get() = when {
            stackCount > 1 -> " ($stackCount times)"
            else           -> ""
        }

    // TODO localize
    fun getSystemMessage(currentUser: UserName?): String {
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
                    null -> "$targetUser has been permanently banned."
                    else -> "$creatorUser banned $targetUser$reasonOrBlank."

                }
            }

            Action.Unban     -> "$creatorUser unbanned $targetUser."
            Action.Mod       -> "$creatorUser modded $targetUser."
            Action.Unmod     -> "$creatorUser unmodded $targetUser."
            Action.Delete    -> "$creatorUser deleted message from $targetUser saying: \"$reason\"."
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
                stackCount = if (target != null && duration != null) 1 else 0
            )
        }

        fun parseModerationAction(timestamp: Instant, channel: UserName, data: ModerationActionData): ModerationMessage {
            val seconds = data.args?.getOrNull(1)?.toIntOrNull()
            val duration = parseDuration(seconds, data)
            val targetUser = parseTargetUser(data)
            val reason = parseReason(data)

            return ModerationMessage(
                timestamp = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                id = data.msgId ?: UUID.randomUUID().toString(),
                channel = channel,
                action = data.moderationAction.toAction(),
                creatorUser = data.creator,
                targetUser = targetUser,
                durationSeconds = seconds,
                duration = duration,
                reason = reason,
                stackCount = if (data.targetUserName != null && duration != null) 1 else 0
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