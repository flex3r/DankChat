package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.ChannelModerateAction
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.ChannelModerateDto
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.pubsub.dto.moderation.ModerationActionData
import com.flxrs.dankchat.data.twitch.pubsub.dto.moderation.ModerationActionType
import com.flxrs.dankchat.utils.DateTimeUtils
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

data class ModerationMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: Set<Highlight> = emptySet(),
    val channel: UserName,
    val action: Action,
    val creatorUserDisplay: DisplayName? = null,
    val targetUser: UserName? = null,
    val targetUserDisplay: DisplayName? = null,
    val sourceBroadcasterDisplay: DisplayName? = null,
    val targetMsgId: String? = null,
    val durationInt: Int? = null,
    val duration: String? = null,
    val reason: String? = null,
    val fromEventSource: Boolean = false,
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
        Vip,
        Unvip,
        Warn,
        Raid,
        Unraid,
        EmoteOnly,
        EmoteOnlyOff,
        Followers,
        FollowersOff,
        UniqueChat,
        UniqueChatOff,
        Slow,
        SlowOff,
        Subscribers,
        SubscribersOff,
        SharedBan,
        SharedUnban,
        SharedTimeout,
        SharedUntimeout,
        SharedDelete,
    }

    private val durationOrBlank get() = duration?.let { " for $it" }.orEmpty()
    private val quotedReasonOrBlank get() = reason.takeUnless { it.isNullOrBlank() }?.let { ": \"$it\"" }.orEmpty()
    private val reasonsOrBlank get() = reason.takeUnless { it.isNullOrBlank() }?.let { ": $it" }.orEmpty()
    private fun getTrimmedReasonOrBlank(showDeletedMessage: Boolean): String {
        if (!showDeletedMessage) return ""

        val fullReason = reason.orEmpty()
        val trimmed = when {
            fullReason.length > 50 -> "${fullReason.take(50)}…"
            else                   -> fullReason
        }
        return " saying: \"$trimmed\""
    }

    private val creatorOrBlank get() = creatorUserDisplay?.let { " by $it" }.orEmpty()
    private val countOrBlank
        get() = when {
            stackCount > 1 -> " ($stackCount times)"
            else           -> ""
        }

    // TODO localize
    fun getSystemMessage(currentUser: UserName?, showDeletedMessage: Boolean): String {
        return when (action) {
            Action.Timeout         -> when (targetUser) {
                currentUser -> "You were timed out$durationOrBlank$creatorOrBlank$quotedReasonOrBlank.$countOrBlank"
                else        -> when (creatorUserDisplay) {
                    null -> "$targetUserDisplay has been timed out$durationOrBlank.$countOrBlank" // irc
                    else -> "$creatorUserDisplay timed out $targetUserDisplay$durationOrBlank.$countOrBlank"
                }
            }

            Action.Untimeout       -> "$creatorUserDisplay untimedout $targetUserDisplay."
            Action.Ban             -> when (targetUser) {
                currentUser -> "You were banned$creatorOrBlank$quotedReasonOrBlank."
                else        -> when (creatorUserDisplay) {
                    null -> "$targetUserDisplay has been permanently banned." // irc
                    else -> "$creatorUserDisplay banned $targetUserDisplay$quotedReasonOrBlank."

                }
            }

            Action.Unban           -> "$creatorUserDisplay unbanned $targetUserDisplay."
            Action.Mod             -> "$creatorUserDisplay modded $targetUserDisplay."
            Action.Unmod           -> "$creatorUserDisplay unmodded $targetUserDisplay."
            Action.Delete          -> when (creatorUserDisplay) {
                null -> "A message from $targetUserDisplay was deleted${getTrimmedReasonOrBlank(showDeletedMessage)}."
                else -> "$creatorUserDisplay deleted message from $targetUserDisplay${getTrimmedReasonOrBlank(showDeletedMessage)}."
            }

            Action.Clear           -> when (creatorUserDisplay) {
                null -> "Chat has been cleared by a moderator."
                else -> "$creatorUserDisplay cleared the chat."
            }

            Action.Vip             -> "$creatorUserDisplay has added $targetUserDisplay as a VIP of this channel."
            Action.Unvip           -> "$creatorUserDisplay has removed $targetUserDisplay as a VIP of this channel."
            Action.Warn            -> "$creatorUserDisplay has warned $targetUserDisplay${reasonsOrBlank.ifBlank { "." }}"
            Action.Raid            -> "$creatorUserDisplay initiated a raid to $targetUserDisplay."
            Action.Unraid          -> "$creatorUserDisplay canceled the raid to $targetUserDisplay."
            Action.EmoteOnly       -> "$creatorUserDisplay turned on emote-only mode."
            Action.EmoteOnlyOff    -> "$creatorUserDisplay turned off emote-only mode."
            Action.Followers       -> "$creatorUserDisplay turned on followers-only mode.${durationInt?.takeIf { it > 0 }?.let { " ($it minutes)" }.orEmpty()}"
            Action.FollowersOff    -> "$creatorUserDisplay turned off followers-only mode."
            Action.UniqueChat      -> "$creatorUserDisplay turned on unique-chat mode."
            Action.UniqueChatOff   -> "$creatorUserDisplay turned off unique-chat mode."
            Action.Slow            -> "$creatorUserDisplay turned on slow mode.${durationInt?.let { " ($it seconds)" }.orEmpty()}"
            Action.SlowOff         -> "$creatorUserDisplay turned off slow mode."
            Action.Subscribers     -> "$creatorUserDisplay turned on subscribers-only mode."
            Action.SubscribersOff  -> "$creatorUserDisplay turned off subscribers-only mode."
            Action.SharedTimeout   -> "$creatorUserDisplay timed out $targetUserDisplay$durationOrBlank in $sourceBroadcasterDisplay.$countOrBlank"
            Action.SharedUntimeout -> "$creatorUserDisplay untimedout $targetUserDisplay in $sourceBroadcasterDisplay."
            Action.SharedBan       -> "$creatorUserDisplay banned $targetUserDisplay in $sourceBroadcasterDisplay$quotedReasonOrBlank."
            Action.SharedUnban     -> "$creatorUserDisplay unbanned $targetUserDisplay in $sourceBroadcasterDisplay."
            Action.SharedDelete    -> "$creatorUserDisplay deleted message from $targetUserDisplay in $sourceBroadcasterDisplay${getTrimmedReasonOrBlank(showDeletedMessage)}"
        }
    }

    val canClearMessages: Boolean = action in listOf(Action.Clear, Action.Ban, Action.Timeout, Action.SharedTimeout, Action.SharedBan)
    val canStack: Boolean = canClearMessages && action != Action.Clear

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
                targetUserDisplay = target?.toDisplayName(),
                targetUser = target?.toUserName(),
                durationInt = durationSeconds,
                duration = duration,
                stackCount = if (target != null && duration != null) 1 else 0,
                fromEventSource = false,
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
                targetUserDisplay = target?.toDisplayName(),
                targetUser = target?.toUserName(),
                targetMsgId = targetMsgId,
                reason = reason,
                fromEventSource = false,
            )
        }

        fun parseModerationAction(timestamp: Instant, channel: UserName, data: ModerationActionData): ModerationMessage {
            val seconds = data.args?.getOrNull(1)?.toIntOrNull()
            val duration = parseDuration(seconds, data)
            val targetUser = parseTargetUser(data)
            val targetMsgId = parseTargetMsgId(data)
            val reason = parseReason(data)
            val timeZone = TimeZone.currentSystemDefault()

            return ModerationMessage(
                timestamp = timestamp.toLocalDateTime(timeZone).toInstant(timeZone).toEpochMilliseconds(),
                id = data.msgId ?: UUID.randomUUID().toString(),
                channel = channel,
                action = data.moderationAction.toAction(),
                creatorUserDisplay = data.creator?.toDisplayName(),
                targetUser = targetUser,
                targetUserDisplay = targetUser?.toDisplayName(),
                targetMsgId = targetMsgId,
                durationInt = seconds,
                duration = duration,
                reason = reason,
                stackCount = if (data.targetUserName != null && duration != null) 1 else 0,
                fromEventSource = true,
            )
        }

        fun parseModerationAction(id: String, timestamp: Instant, channel: UserName, data: ChannelModerateDto): ModerationMessage {
            val timeZone = TimeZone.currentSystemDefault()
            val timestampMillis = timestamp.toLocalDateTime(timeZone).toInstant(timeZone).toEpochMilliseconds()
            val duration = parseDuration(timestamp, data)
            val formattedDuration = duration?.let { DateTimeUtils.formatSeconds(it) }
            val userPair = parseTargetUser(data)
            val targetMsgId = parseTargetMsgId(data)
            val reason = parseReason(data)

            return ModerationMessage(
                timestamp = timestampMillis,
                id = id,
                channel = channel,
                action = data.action.toAction(),
                creatorUserDisplay = data.moderatorUserName,
                sourceBroadcasterDisplay = data.sourceBroadcasterUserName,
                targetUser = userPair?.first,
                targetUserDisplay = userPair?.second,
                targetMsgId = targetMsgId,
                durationInt = duration,
                duration = formattedDuration,
                reason = reason,
                fromEventSource = true,
            )
        }

        private fun parseDuration(seconds: Int?, data: ModerationActionData): String? = when (data.moderationAction) {
            ModerationActionType.Timeout -> seconds?.let { DateTimeUtils.formatSeconds(seconds) }
            else                         -> null
        }

        private fun parseDuration(timestamp: Instant, data: ChannelModerateDto): Int? = when (data.action) {
            ChannelModerateAction.Timeout           -> data.timeout?.let { it.expiresAt.epochSeconds - timestamp.epochSeconds }?.toInt()
            ChannelModerateAction.SharedChatTimeout -> data.sharedChatTimeout?.let { it.expiresAt.epochSeconds - timestamp.epochSeconds }?.toInt()
            ChannelModerateAction.Followers         -> data.followers?.followDurationMinutes
            ChannelModerateAction.Slow              -> data.slow?.waitTimeSeconds
            else                                    -> null
        }

        private fun parseReason(data: ModerationActionData): String? = when (data.moderationAction) {
            ModerationActionType.Ban,
            ModerationActionType.Delete  -> data.args?.getOrNull(1)

            ModerationActionType.Timeout -> data.args?.getOrNull(2)
            else                         -> null
        }

        private fun parseReason(data: ChannelModerateDto): String? = when (data.action) {
            ChannelModerateAction.Ban               -> data.ban?.reason
            ChannelModerateAction.Delete            -> data.delete?.messageBody
            ChannelModerateAction.Timeout           -> data.timeout?.reason
            ChannelModerateAction.SharedChatBan     -> data.sharedChatBan?.reason
            ChannelModerateAction.SharedChatDelete  -> data.sharedChatDelete?.messageBody
            ChannelModerateAction.SharedChatTimeout -> data.sharedChatTimeout?.reason
            ChannelModerateAction.Warn              -> data.warn?.let { listOfNotNull(it.reason).plus(it.chatRulesCited.orEmpty()).joinToString() }
            else                                    -> null
        }

        private fun parseTargetUser(data: ModerationActionData): UserName? = when (data.moderationAction) {
            ModerationActionType.Delete -> data.args?.getOrNull(0)?.toUserName()
            else                        -> data.targetUserName
        }

        private fun parseTargetUser(data: ChannelModerateDto): Pair<UserName, DisplayName>? = when (data.action) {
            ChannelModerateAction.Timeout             -> data.timeout?.let { it.userLogin to it.userName }
            ChannelModerateAction.Untimeout           -> data.untimeout?.let { it.userLogin to it.userName }
            ChannelModerateAction.Ban                 -> data.ban?.let { it.userLogin to it.userName }
            ChannelModerateAction.Unban               -> data.unban?.let { it.userLogin to it.userName }
            ChannelModerateAction.Mod                 -> data.mod?.let { it.userLogin to it.userName }
            ChannelModerateAction.Unmod               -> data.unmod?.let { it.userLogin to it.userName }
            ChannelModerateAction.Delete              -> data.delete?.let { it.userLogin to it.userName }
            ChannelModerateAction.Vip                 -> data.vip?.let { it.userLogin to it.userName }
            ChannelModerateAction.Unvip               -> data.unvip?.let { it.userLogin to it.userName }
            ChannelModerateAction.Warn                -> data.warn?.let { it.userLogin to it.userName }
            ChannelModerateAction.Raid                -> data.raid?.let { it.userLogin to it.userName }
            ChannelModerateAction.Unraid              -> data.unraid?.let { it.userLogin to it.userName }
            ChannelModerateAction.SharedChatTimeout   -> data.sharedChatTimeout?.let { it.userLogin to it.userName }
            ChannelModerateAction.SharedChatUntimeout -> data.sharedChatUntimeout?.let { it.userLogin to it.userName }
            ChannelModerateAction.SharedChatBan       -> data.sharedChatBan?.let { it.userLogin to it.userName }
            ChannelModerateAction.SharedChatUnban     -> data.sharedChatUnban?.let { it.userLogin to it.userName }
            ChannelModerateAction.SharedChatDelete    -> data.sharedChatDelete?.let { it.userLogin to it.userName }
            else                                      -> null
        }

        private fun parseTargetMsgId(data: ModerationActionData): String? = when (data.moderationAction) {
            ModerationActionType.Delete -> data.args?.getOrNull(2)
            else                        -> null
        }

        private fun parseTargetMsgId(data: ChannelModerateDto): String? = when (data.action) {
            ChannelModerateAction.Delete           -> data.delete?.messageId
            ChannelModerateAction.SharedChatDelete -> data.sharedChatDelete?.messageId
            else                                   -> null
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

        private fun ChannelModerateAction.toAction() = when (this) {
            ChannelModerateAction.Timeout             -> Action.Timeout
            ChannelModerateAction.Untimeout           -> Action.Untimeout
            ChannelModerateAction.Ban                 -> Action.Ban
            ChannelModerateAction.Unban               -> Action.Unban
            ChannelModerateAction.Mod                 -> Action.Mod
            ChannelModerateAction.Unmod               -> Action.Unmod
            ChannelModerateAction.Clear               -> Action.Clear
            ChannelModerateAction.Delete              -> Action.Delete
            ChannelModerateAction.Vip                 -> Action.Vip
            ChannelModerateAction.Unvip               -> Action.Unvip
            ChannelModerateAction.Warn                -> Action.Warn
            ChannelModerateAction.Raid                -> Action.Raid
            ChannelModerateAction.Unraid              -> Action.Unraid
            ChannelModerateAction.EmoteOnly           -> Action.EmoteOnly
            ChannelModerateAction.EmoteOnlyOff        -> Action.EmoteOnlyOff
            ChannelModerateAction.Followers           -> Action.Followers
            ChannelModerateAction.FollowersOff        -> Action.FollowersOff
            ChannelModerateAction.UniqueChat          -> Action.UniqueChat
            ChannelModerateAction.UniqueChatOff       -> Action.UniqueChatOff
            ChannelModerateAction.Slow                -> Action.Slow
            ChannelModerateAction.SlowOff             -> Action.SlowOff
            ChannelModerateAction.Subscribers         -> Action.Subscribers
            ChannelModerateAction.SubscribersOff      -> Action.SubscribersOff
            ChannelModerateAction.SharedChatTimeout   -> Action.SharedTimeout
            ChannelModerateAction.SharedChatUntimeout -> Action.SharedUntimeout
            ChannelModerateAction.SharedChatBan       -> Action.SharedBan
            ChannelModerateAction.SharedChatUnban     -> Action.SharedUnban
            ChannelModerateAction.SharedChatDelete    -> Action.SharedDelete
            else                                      -> error("Unexpected moderation action $this")
        }
    }
}
