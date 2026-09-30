package com.flxrs.dankchat.ui.chat

import androidx.compose.ui.graphics.Color
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.chat.ChatImportance
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmoteType
import com.flxrs.dankchat.data.twitch.message.AnnouncementColor
import com.flxrs.dankchat.data.twitch.message.AutomodMessage
import com.flxrs.dankchat.data.twitch.message.Highlight
import com.flxrs.dankchat.data.twitch.message.HighlightType
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.data.twitch.message.ModerationMessage.Action
import com.flxrs.dankchat.data.twitch.message.NoticeMessage
import com.flxrs.dankchat.data.twitch.message.PointRedemptionMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.data.twitch.message.aliasOrFormattedName
import com.flxrs.dankchat.data.twitch.message.highestPriorityHighlight
import com.flxrs.dankchat.data.twitch.message.hypeChatInfo
import com.flxrs.dankchat.data.twitch.message.isAnimatedMessage
import com.flxrs.dankchat.data.twitch.message.isAsciiArt
import com.flxrs.dankchat.data.twitch.message.isElevatedMessage
import com.flxrs.dankchat.data.twitch.message.isGigantifiedEmote
import com.flxrs.dankchat.data.twitch.message.recipientAliasOrFormattedName
import com.flxrs.dankchat.data.twitch.message.senderAliasOrFormattedName
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.chat.ChatSettings
import com.flxrs.dankchat.ui.chat.messages.common.findLinks
import com.flxrs.dankchat.utils.DateTimeUtils
import com.flxrs.dankchat.utils.TextResource
import com.flxrs.dankchat.utils.extensions.parseColorOrNull
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.annotation.Single

/**
 * Maps domain Message objects to Compose UI state objects.
 * Pre-computed all rendering decisions to minimize work during composition.
 */
@Single
class ChatMessageMapper(
    private val usersRepository: UsersRepository,
) {
    fun mapToUiState(
        item: ChatItem,
        chatSettings: ChatSettings,
        preferenceStore: DankChatPreferenceStore,
        isAlternateBackground: Boolean,
    ): ChatMessageUiState {
        val textAlpha =
            when (item.importance) {
                ChatImportance.SYSTEM -> 1f
                ChatImportance.DELETED -> 0.5f
                ChatImportance.REGULAR -> 1f
            }

        return when (val msg = item.message) {
            is SystemMessage -> {
                msg.toSystemMessageUi(
                    tag = item.tag,
                    chatSettings = chatSettings,
                    isAlternateBackground = isAlternateBackground,
                    textAlpha = textAlpha,
                )
            }

            is NoticeMessage -> {
                msg.toNoticeMessageUi(
                    tag = item.tag,
                    chatSettings = chatSettings,
                    isAlternateBackground = isAlternateBackground,
                    textAlpha = textAlpha,
                )
            }

            is UserNoticeMessage -> {
                msg.toUserNoticeMessageUi(
                    tag = item.tag,
                    chatSettings = chatSettings,
                    isAlternateBackground = isAlternateBackground,
                    textAlpha = textAlpha,
                )
            }

            is PrivMessage -> {
                msg.toPrivMessageUi(
                    tag = item.tag,
                    chatSettings = chatSettings,
                    isAlternateBackground = isAlternateBackground,
                    isMentionTab = item.isMentionTab,
                    isInReplies = item.isInReplies,
                    textAlpha = textAlpha,
                )
            }

            is AutomodMessage -> {
                msg.toAutomodMessageUi(
                    tag = item.tag,
                    chatSettings = chatSettings,
                    textAlpha = textAlpha,
                )
            }

            is ModerationMessage -> {
                msg.toModerationMessageUi(
                    tag = item.tag,
                    chatSettings = chatSettings,
                    preferenceStore = preferenceStore,
                    isAlternateBackground = isAlternateBackground,
                    textAlpha = textAlpha,
                )
            }

            is PointRedemptionMessage -> {
                msg.toPointRedemptionMessageUi(
                    tag = item.tag,
                    chatSettings = chatSettings,
                    textAlpha = textAlpha,
                )
            }

            is WhisperMessage -> {
                msg.toWhisperMessageUi(
                    tag = item.tag,
                    chatSettings = chatSettings,
                    isAlternateBackground = isAlternateBackground,
                    textAlpha = textAlpha,
                    currentUserName = preferenceStore.userName,
                )
            }
        }
    }

    fun List<ChatMessageUiState>.withHighlightLayout(showLineSeparator: Boolean): List<ChatMessageUiState> = mapIndexed { index, message ->
        val above = getOrNull(index - 1)
        val below = getOrNull(index + 1)
        val hasSameAbove = message.hasSameHighlightBackground(above)
        val hasSameBelow = message.hasSameHighlightBackground(below)

        val roundedTop = message.isHighlighted && !hasSameAbove
        val roundedBottom = message.isHighlighted && !hasSameBelow

        val isHighlightBoundary = (message.isHighlighted && !hasSameBelow) ||
            (below != null && below.isHighlighted && !below.hasSameHighlightBackground(message))
        val showDivider = showLineSeparator && below != null && !isHighlightBoundary

        message.withLayout(roundedTop, roundedBottom, showDivider)
    }

    private fun SystemMessage.toSystemMessageUi(
        tag: Int,
        chatSettings: ChatSettings,
        isAlternateBackground: Boolean,
        textAlpha: Float,
    ): ChatMessageUiState.SystemMessageUi {
        val backgroundColors = calculateCheckeredBackgroundColors(isAlternateBackground, false)
        val timestamp =
            if (chatSettings.showTimestamps && timestamp > 0L) {
                DateTimeUtils.timestampToLocalTime(timestamp, chatSettings.formatter)
            } else {
                ""
            }

        val message =
            when (type) {
                is SystemMessageType.Disconnected -> {
                    TextResource.Res(R.string.system_message_disconnected)
                }

                is SystemMessageType.Connected -> {
                    TextResource.Res(R.string.system_message_connected)
                }

                is SystemMessageType.Reconnected -> {
                    TextResource.Res(R.string.system_message_reconnected)
                }

                is SystemMessageType.LoginExpired -> {
                    TextResource.Res(R.string.login_expired)
                }

                is SystemMessageType.ChannelNonExistent -> {
                    TextResource.Res(R.string.system_message_channel_non_existent)
                }

                is SystemMessageType.MessageHistoryIgnored -> {
                    TextResource.Res(R.string.system_message_history_ignored)
                }

                is SystemMessageType.MessageHistoryIncomplete -> {
                    TextResource.Res(R.string.system_message_history_recovering)
                }

                is SystemMessageType.ChannelBTTVEmotesFailed -> {
                    TextResource.Res(R.string.system_message_bttv_emotes_failed, persistentListOf(type.status))
                }

                is SystemMessageType.ChannelFFZEmotesFailed -> {
                    TextResource.Res(R.string.system_message_ffz_emotes_failed, persistentListOf(type.status))
                }

                is SystemMessageType.ChannelSevenTVEmotesFailed -> {
                    TextResource.Res(R.string.system_message_7tv_emotes_failed, persistentListOf(type.status))
                }

                SystemMessageType.ChannelFFZEmotesCachedFallback -> {
                    TextResource.Res(R.string.system_message_ffz_emotes_cached_fallback)
                }

                SystemMessageType.ChannelBTTVEmotesCachedFallback -> {
                    TextResource.Res(R.string.system_message_bttv_emotes_cached_fallback)
                }

                SystemMessageType.ChannelSevenTVEmotesCachedFallback -> {
                    TextResource.Res(R.string.system_message_7tv_emotes_cached_fallback)
                }

                is SystemMessageType.Custom -> {
                    type.message
                }

                is SystemMessageType.Debug -> {
                    TextResource.Plain(type.message)
                }

                is SystemMessageType.SendNotLoggedIn -> {
                    TextResource.Res(R.string.system_message_send_not_logged_in)
                }

                is SystemMessageType.SendChannelNotResolved -> {
                    TextResource.Res(R.string.system_message_send_channel_not_resolved, persistentListOf(type.channel))
                }

                is SystemMessageType.SendNotDelivered -> {
                    TextResource.Res(R.string.system_message_send_not_delivered)
                }

                is SystemMessageType.SendDropped -> {
                    TextResource.Res(R.string.system_message_send_dropped, persistentListOf(type.reason, type.code))
                }

                is SystemMessageType.SendMissingScopes -> {
                    TextResource.Res(R.string.system_message_send_missing_scopes)
                }

                is SystemMessageType.SendNotAuthorized -> {
                    TextResource.Res(R.string.system_message_send_not_authorized)
                }

                is SystemMessageType.SendMessageTooLarge -> {
                    TextResource.Res(R.string.system_message_send_message_too_large)
                }

                is SystemMessageType.SendRateLimited -> {
                    TextResource.Res(R.string.system_message_send_rate_limited)
                }

                is SystemMessageType.SendFailed -> {
                    TextResource.Res(R.string.system_message_send_failed, persistentListOf(type.message.orEmpty()))
                }

                is SystemMessageType.MessageHistoryUnavailable -> {
                    when (type.status) {
                        null -> TextResource.Res(R.string.system_message_history_unavailable)
                        else -> TextResource.Res(R.string.system_message_history_unavailable_detailed, persistentListOf(type.status))
                    }
                }

                is SystemMessageType.ChannelSevenTVEmoteAdded -> {
                    TextResource.Res(R.string.system_message_7tv_emote_added, persistentListOf(type.actorName, type.emoteName))
                }

                is SystemMessageType.ChannelSevenTVEmoteRemoved -> {
                    TextResource.Res(R.string.system_message_7tv_emote_removed, persistentListOf(type.actorName, type.emoteName))
                }

                is SystemMessageType.ChannelSevenTVEmoteRenamed -> {
                    TextResource.Res(
                        R.string.system_message_7tv_emote_renamed,
                        persistentListOf(type.actorName, type.oldEmoteName, type.emoteName),
                    )
                }

                is SystemMessageType.ChannelSevenTVEmoteSetChanged -> {
                    TextResource.Res(R.string.system_message_7tv_emote_set_changed, persistentListOf(type.actorName, type.newEmoteSetName))
                }

                is SystemMessageType.StreamLive -> {
                    val title = type.title?.takeIf { chatSettings.showStreamTitleInLiveMessage }
                    when (title) {
                        null -> TextResource.Res(R.string.system_message_stream_live, persistentListOf(type.channel))
                        else -> TextResource.Res(R.string.system_message_stream_live_with_title, persistentListOf(type.channel, title))
                    }
                }

                is SystemMessageType.StreamOffline -> {
                    TextResource.Res(R.string.system_message_stream_offline, persistentListOf(type.channel))
                }

                is SystemMessageType.AutomodActionFailed -> {
                    val actionRes = TextResource.Res(if (type.allow) R.string.automod_allow else R.string.automod_deny)
                    val errorResId =
                        when (type.statusCode) {
                            400 -> R.string.automod_error_already_processed
                            401 -> R.string.automod_error_not_authenticated
                            403 -> R.string.automod_error_not_authorized
                            404 -> R.string.automod_error_not_found
                            else -> R.string.automod_error_unknown
                        }
                    TextResource.Res(errorResId, persistentListOf(actionRes))
                }

                is SystemMessageType.PinnedMessageActionFailed -> {
                    val status = type.statusCode?.toString() ?: "0"
                    when {
                        type.pin && type.statusCode == 409 -> TextResource.Res(R.string.system_message_pin_already_pinned)
                        type.pin -> TextResource.Res(R.string.system_message_pin_failed, persistentListOf(status))
                        else -> TextResource.Res(R.string.system_message_unpin_failed, persistentListOf(status))
                    }
                }
            }

        val boldText =
            when (type) {
                is SystemMessageType.StreamLive -> type.channel.value
                is SystemMessageType.StreamOffline -> type.channel.value
                else -> null
            }

        return ChatMessageUiState.SystemMessageUi(
            id = id,
            tag = tag,
            timestamp = timestamp,
            lightBackgroundColor = backgroundColors.light,
            darkBackgroundColor = backgroundColors.dark,
            textAlpha = textAlpha,
            message = message,
            boldText = boldText,
        )
    }

    private fun NoticeMessage.toNoticeMessageUi(
        tag: Int,
        chatSettings: ChatSettings,
        isAlternateBackground: Boolean,
        textAlpha: Float,
    ): ChatMessageUiState.NoticeMessageUi {
        val backgroundColors = calculateCheckeredBackgroundColors(isAlternateBackground, false)
        val timestamp =
            if (chatSettings.showTimestamps && timestamp > 0L) {
                DateTimeUtils.timestampToLocalTime(timestamp, chatSettings.formatter)
            } else {
                ""
            }

        return ChatMessageUiState.NoticeMessageUi(
            id = id,
            tag = tag,
            timestamp = timestamp,
            lightBackgroundColor = backgroundColors.light,
            darkBackgroundColor = backgroundColors.dark,
            textAlpha = textAlpha,
            message = message,
            links = findLinks(message).toImmutableList(),
        )
    }

    private fun UserNoticeMessage.toUserNoticeMessageUi(
        tag: Int,
        chatSettings: ChatSettings,
        isAlternateBackground: Boolean,
        textAlpha: Float,
    ): ChatMessageUiState.UserNoticeMessageUi {
        val highlightType =
            highlights.firstOrNull {
                it.type == HighlightType.Subscription ||
                    it.type == HighlightType.Announcement ||
                    it.type == HighlightType.WatchStreak
            }
        val backgroundColors =
            when {
                highlightType != null -> highlights.toBackgroundColors()
                else -> calculateCheckeredBackgroundColors(isAlternateBackground, false)
            }
        val timestamp =
            if (chatSettings.showTimestamps && timestamp > 0L) {
                DateTimeUtils.timestampToLocalTime(timestamp, chatSettings.formatter)
            } else {
                ""
            }

        val displayName = tags["display-name"].orEmpty()
        val login = tags["login"]?.toUserName()
        val ircColor =
            tags["color"]?.parseColorOrNull()
                ?: login?.let { usersRepository.getCachedUserColor(it) }
        val rawNameColor = resolveNameColor(null, ircColor, tags["user-id"]?.toUserId(), chatSettings)

        return ChatMessageUiState.UserNoticeMessageUi(
            id = id,
            tag = tag,
            timestamp = timestamp,
            lightBackgroundColor = backgroundColors.light,
            darkBackgroundColor = backgroundColors.dark,
            textAlpha = textAlpha,
            isHighlighted = highlightType != null,
            channel = channel,
            userId = tags["user-id"]?.toUserId(),
            userName = login,
            message = message,
            links = findLinks(message).toImmutableList(),
            displayName = displayName,
            rawNameColor = rawNameColor,
            shouldHighlight = highlightType != null,
        )
    }

    private fun ModerationMessage.toModerationMessageUi(
        tag: Int,
        chatSettings: ChatSettings,
        preferenceStore: DankChatPreferenceStore,
        isAlternateBackground: Boolean,
        textAlpha: Float,
    ): ChatMessageUiState.ModerationMessageUi {
        val backgroundColors = calculateCheckeredBackgroundColors(isAlternateBackground, false)
        val timestamp =
            if (chatSettings.showTimestamps && timestamp > 0L) {
                DateTimeUtils.timestampToLocalTime(timestamp, chatSettings.formatter)
            } else {
                ""
            }

        val arguments =
            buildList {
                when (action) {
                    is Action.Timeout -> add(action.duration)
                    is Action.SharedTimeout -> add(action.duration)
                    else -> Unit
                }
                reason?.takeIf { it.isNotBlank() }?.let(::add)
                sourceBroadcasterDisplay?.toString()?.let(::add)
            }.toImmutableList()

        return ChatMessageUiState.ModerationMessageUi(
            id = id,
            tag = tag,
            timestamp = timestamp,
            lightBackgroundColor = backgroundColors.light,
            darkBackgroundColor = backgroundColors.dark,
            textAlpha = textAlpha,
            channel = channel,
            message = getSystemMessage(preferenceStore.userName, chatSettings.showTimedOutMessages),
            creatorName = creatorUserDisplay?.toString(),
            targetName = targetUserDisplay?.toString(),
            creatorUserName = creatorUser,
            targetUserName = targetUser,
            creatorColor = creatorUser?.let { usersRepository.getCachedUserColor(it) } ?: Message.DEFAULT_COLOR,
            targetColor = targetUser?.let { usersRepository.getCachedUserColor(it) } ?: Message.DEFAULT_COLOR,
            arguments = arguments,
        )
    }

    private fun AutomodMessage.toAutomodMessageUi(
        tag: Int,
        chatSettings: ChatSettings,
        textAlpha: Float,
    ): ChatMessageUiState.AutomodMessageUi {
        val timestamp =
            if (chatSettings.showTimestamps && timestamp > 0L) {
                DateTimeUtils.timestampToLocalTime(timestamp, chatSettings.formatter)
            } else {
                ""
            }

        val uiStatus =
            when (status) {
                AutomodMessage.Status.Pending -> ChatMessageUiState.AutomodMessageUi.AutomodMessageStatus.Pending
                AutomodMessage.Status.Approved -> ChatMessageUiState.AutomodMessageUi.AutomodMessageStatus.Approved
                AutomodMessage.Status.Denied -> ChatMessageUiState.AutomodMessageUi.AutomodMessageStatus.Denied
                AutomodMessage.Status.Expired -> ChatMessageUiState.AutomodMessageUi.AutomodMessageStatus.Expired
            }

        return ChatMessageUiState.AutomodMessageUi(
            id = id,
            tag = tag,
            timestamp = timestamp,
            lightBackgroundColor = Color.Unspecified,
            darkBackgroundColor = Color.Unspecified,
            textAlpha = textAlpha,
            heldMessageId = heldMessageId,
            channel = channel,
            badges =
                badges
                    .mapIndexed { index, badge ->
                        BadgeUi(
                            url = badge.url,
                            badge = badge,
                            position = index,
                            drawableResId =
                                when (badge.badgeTag) {
                                    "automod/1" -> R.drawable.ic_automod_badge
                                    else -> null
                                },
                        )
                    }.toImmutableList(),
            userDisplayName = userName.formatWithDisplayName(userDisplayName),
            rawNameColor = color ?: Message.DEFAULT_COLOR,
            messageText = messageText?.takeIf { it.isNotEmpty() },
            reason = reason,
            status = uiStatus,
            isUserSide = isUserSide,
        )
    }

    private fun PrivMessage.toPrivMessageUi(
        tag: Int,
        chatSettings: ChatSettings,
        isAlternateBackground: Boolean,
        isMentionTab: Boolean,
        isInReplies: Boolean,
        textAlpha: Float,
    ): ChatMessageUiState.PrivMessageUi {
        val backgroundColors =
            when {
                timedOut && !chatSettings.showTimedOutMessages -> BackgroundColors(Color.Transparent, Color.Transparent)
                highlights.isNotEmpty() -> highlights.toBackgroundColors()
                else -> calculateCheckeredBackgroundColors(isAlternateBackground, true)
            }

        val timestamp =
            if (chatSettings.showTimestamps && timestamp > 0L) {
                DateTimeUtils.timestampToLocalTime(timestamp, chatSettings.formatter)
            } else {
                ""
            }

        val nameText =
            when {
                !chatSettings.showUsernames -> ""
                isAction -> "$aliasOrFormattedName "
                aliasOrFormattedName.isBlank() -> ""
                else -> "$aliasOrFormattedName: "
            }

        val allowedBadges = badges.filter { it.type in chatSettings.visibleBadgeTypes }
        val badgeUis =
            allowedBadges
                .mapIndexed { index, badge ->
                    BadgeUi(
                        url = badge.url,
                        badge = badge,
                        position = index,
                    )
                }.toImmutableList()

        val emoteUis =
            emotes
                .groupBy { it.position }
                .map { (position, emoteGroup) ->
                    // Check if any emote in the group is animated - we need to check the type
                    val hasAnimated =
                        emoteGroup.any { emote ->
                            when (emote.type) {
                                is ChatMessageEmoteType.TwitchEmote -> false

                                // Twitch emotes can be animated but we don't have that info here
                                is ChatMessageEmoteType.ChannelFFZEmote,
                                is ChatMessageEmoteType.GlobalFFZEmote,
                                is ChatMessageEmoteType.ChannelBTTVEmote,
                                is ChatMessageEmoteType.GlobalBTTVEmote,
                                -> true

                                // Assume third-party can be animated
                                is ChatMessageEmoteType.ChannelSevenTVEmote,
                                is ChatMessageEmoteType.GlobalSevenTVEmote,
                                -> true

                                is ChatMessageEmoteType.Cheermote -> true
                            }
                        }

                    val firstEmote = emoteGroup.first()
                    EmoteUi(
                        code = firstEmote.code,
                        urls = emoteGroup.map { it.url }.toImmutableList(),
                        position = position,
                        isAnimated = hasAnimated,
                        isTwitch = emoteGroup.any { it.isTwitch },
                        scale = firstEmote.scale,
                        emotes = emoteGroup.toImmutableList(),
                        cheerAmount = firstEmote.cheerAmount,
                        cheerColor = firstEmote.cheerColor?.let { Color(it) },
                    )
                }.toImmutableList()

        val threadUi =
            if (thread != null && !isInReplies) {
                ThreadUi(
                    rootId = thread.rootId,
                    userName = thread.name.value,
                    message = thread.message,
                    rawNameColor = usersRepository.getCachedUserColor(thread.name) ?: Message.DEFAULT_COLOR,
                )
            } else {
                null
            }

        val highlightHeader =
            when {
                isGigantifiedEmote -> {
                    TextResource.Res(R.string.highlight_header_gigantified_emote)
                }

                isAnimatedMessage -> {
                    TextResource.Res(R.string.highlight_header_animated_message)
                }

                isElevatedMessage -> {
                    hypeChatInfo?.let { TextResource.Plain(it) }
                        ?: TextResource.Res(R.string.highlight_header_elevated_chat)
                }

                rewardTitle != null -> {
                    TextResource.Res(R.string.highlight_header_reward_no_cost, persistentListOf(rewardTitle))
                }

                highlights.highestPriorityHighlight()?.type == HighlightType.FirstMessage -> {
                    TextResource.Res(R.string.highlight_header_first_time_chat)
                }

                else -> {
                    null
                }
            }

        val fullMessage =
            buildString {
                if (isMentionTab && highlights.any { it.isMention }) {
                    append("#$channel ")
                }
                if (timestamp.isNotEmpty()) {
                    append("$timestamp ")
                }
                append(nameText)
                append(message)
            }

        val rawNameColor = resolveNameColor(userDisplay?.color, color, userId, chatSettings)

        return ChatMessageUiState.PrivMessageUi(
            id = id,
            tag = tag,
            timestamp = timestamp,
            lightBackgroundColor = backgroundColors.light,
            darkBackgroundColor = backgroundColors.dark,
            textAlpha = textAlpha,
            enableRipple = true,
            isHighlighted = highlights.isNotEmpty(),
            channel = channel,
            userId = userId,
            userName = name,
            displayName = displayName,
            badges = badgeUis,
            rawNameColor = rawNameColor,
            nameText = nameText,
            message = message,
            links = findLinks(message).toImmutableList(),
            emotes = emoteUis,
            isAction = isAction,
            isAsciiArt = originalMessage.isAsciiArt(),
            thread = threadUi,
            highlightHeader = highlightHeader,
            highlightHeaderImageUrl = rewardImageUrl,
            highlightHeaderCost = rewardCost,
            highlightHeaderCostSuffix = when {
                isGigantifiedEmote || isAnimatedMessage -> "Bits"
                else -> null
            },
            fullMessage = fullMessage,
        )
    }

    private fun PointRedemptionMessage.toPointRedemptionMessageUi(
        tag: Int,
        chatSettings: ChatSettings,
        textAlpha: Float,
    ): ChatMessageUiState.PointRedemptionMessageUi {
        val backgroundColors = getHighlightColors(HighlightType.ChannelPointRedemption)
        val timestamp =
            if (chatSettings.showTimestamps && timestamp > 0L) {
                DateTimeUtils.timestampToLocalTime(timestamp, chatSettings.formatter)
            } else {
                ""
            }

        val nameText = if (!requiresUserInput) aliasOrFormattedName else null
        val nameColor = usersRepository.getCachedUserColor(name) ?: Message.DEFAULT_COLOR

        return ChatMessageUiState.PointRedemptionMessageUi(
            id = id,
            tag = tag,
            timestamp = timestamp,
            lightBackgroundColor = backgroundColors.light,
            darkBackgroundColor = backgroundColors.dark,
            textAlpha = textAlpha,
            nameText = nameText,
            rawNameColor = nameColor,
            title = title,
            cost = cost,
            rewardImageUrl = rewardImageUrl,
            requiresUserInput = requiresUserInput,
        )
    }

    private fun WhisperMessage.toWhisperMessageUi(
        tag: Int,
        chatSettings: ChatSettings,
        isAlternateBackground: Boolean,
        textAlpha: Float,
        currentUserName: UserName?,
    ): ChatMessageUiState.WhisperMessageUi {
        val inlineWhisperHighlight = highlights.firstOrNull { it.type == HighlightType.InlineWhisper }
        val backgroundColors =
            if (inlineWhisperHighlight != null) {
                setOf(inlineWhisperHighlight).toBackgroundColors()
            } else {
                calculateCheckeredBackgroundColors(isAlternateBackground, true)
            }
        val timestamp =
            if (chatSettings.showTimestamps && timestamp > 0L) {
                DateTimeUtils.timestampToLocalTime(timestamp, chatSettings.formatter)
            } else {
                ""
            }

        val allowedBadges = badges.filter { it.type in chatSettings.visibleBadgeTypes }
        val badgeUis =
            allowedBadges
                .mapIndexed { index, badge ->
                    BadgeUi(
                        url = badge.url,
                        badge = badge,
                        position = index,
                    )
                }.toImmutableList()

        val emoteUis =
            emotes
                .groupBy { it.position }
                .map { (position, emoteGroup) ->
                    // Check if any emote in the group is animated
                    val hasAnimated =
                        emoteGroup.any { emote ->
                            when (emote.type) {
                                is ChatMessageEmoteType.TwitchEmote -> false

                                is ChatMessageEmoteType.ChannelFFZEmote,
                                is ChatMessageEmoteType.GlobalFFZEmote,
                                is ChatMessageEmoteType.ChannelBTTVEmote,
                                is ChatMessageEmoteType.GlobalBTTVEmote,
                                -> true

                                is ChatMessageEmoteType.ChannelSevenTVEmote,
                                is ChatMessageEmoteType.GlobalSevenTVEmote,
                                -> true

                                is ChatMessageEmoteType.Cheermote -> true
                            }
                        }

                    val firstEmote = emoteGroup.first()
                    EmoteUi(
                        code = firstEmote.code,
                        urls = emoteGroup.map { it.url }.toImmutableList(),
                        position = position,
                        isAnimated = hasAnimated,
                        isTwitch = emoteGroup.any { it.isTwitch },
                        scale = firstEmote.scale,
                        emotes = emoteGroup.toImmutableList(),
                        cheerAmount = firstEmote.cheerAmount,
                        cheerColor = firstEmote.cheerColor?.let { Color(it) },
                    )
                }.toImmutableList()

        val fullMessage =
            buildString {
                if (timestamp.isNotEmpty()) {
                    append("$timestamp ")
                }
                append("$senderAliasOrFormattedName -> $recipientAliasOrFormattedName: ")
                append(message)
            }

        val rawSenderColor = resolveNameColor(userDisplay?.color, color, userId, chatSettings)
        val rawRecipientColor = resolveNameColor(recipientDisplay?.color, recipientColor, recipientId, chatSettings)

        return ChatMessageUiState.WhisperMessageUi(
            id = id,
            tag = tag,
            timestamp = timestamp,
            lightBackgroundColor = backgroundColors.light,
            darkBackgroundColor = backgroundColors.dark,
            textAlpha = textAlpha,
            enableRipple = true,
            isHighlighted = inlineWhisperHighlight != null,
            userId = userId ?: error("Whisper must have userId"),
            userName = name,
            displayName = displayName,
            recipientId = recipientId,
            recipientUserName = recipientName,
            recipientDisplayName = recipientDisplayName,
            badges = badgeUis,
            rawSenderColor = rawSenderColor,
            rawRecipientColor = rawRecipientColor,
            senderName = senderAliasOrFormattedName,
            recipientName = recipientAliasOrFormattedName,
            message = message,
            links = findLinks(message).toImmutableList(),
            emotes = emoteUis,
            isAsciiArt = originalMessage.isAsciiArt(),
            fullMessage = fullMessage,
            replyTargetName = if (currentUserName != null && name.value.equals(currentUserName.value, ignoreCase = true)) recipientName else name,
        )
    }

    private fun resolveNameColor(
        customColor: Int?,
        ircColor: Int?,
        userId: UserId?,
        chatSettings: ChatSettings,
    ): Int = when {
        customColor != null -> customColor
        ircColor != null -> ircColor
        chatSettings.colorizeNicknames && userId != null -> getStableColor(userId)
        else -> Message.DEFAULT_COLOR
    }

    data class BackgroundColors(
        val light: Color,
        val dark: Color,
    )

    private fun calculateCheckeredBackgroundColors(
        isAlternateBackground: Boolean,
        enableCheckered: Boolean,
    ): BackgroundColors = if (enableCheckered && isAlternateBackground) {
        BackgroundColors(CHECKERED_LIGHT, CHECKERED_DARK)
    } else {
        BackgroundColors(Color.Transparent, Color.Transparent)
    }

    private fun getHighlightColors(type: HighlightType): BackgroundColors = when (type) {
        HighlightType.Subscription,
        HighlightType.Announcement,
        -> {
            BackgroundColors(
                light = COLOR_SUB_HIGHLIGHT_LIGHT,
                dark = COLOR_SUB_HIGHLIGHT_DARK,
            )
        }

        HighlightType.WatchStreak -> {
            BackgroundColors(
                light = COLOR_WATCH_STREAK_HIGHLIGHT_LIGHT,
                dark = COLOR_WATCH_STREAK_HIGHLIGHT_DARK,
            )
        }

        HighlightType.ChannelPointRedemption -> {
            BackgroundColors(
                light = COLOR_REDEMPTION_HIGHLIGHT_LIGHT,
                dark = COLOR_REDEMPTION_HIGHLIGHT_DARK,
            )
        }

        HighlightType.ElevatedMessage -> {
            BackgroundColors(
                light = COLOR_ELEVATED_MESSAGE_HIGHLIGHT_LIGHT,
                dark = COLOR_ELEVATED_MESSAGE_HIGHLIGHT_DARK,
            )
        }

        HighlightType.FirstMessage -> {
            BackgroundColors(
                light = COLOR_FIRST_MESSAGE_HIGHLIGHT_LIGHT,
                dark = COLOR_FIRST_MESSAGE_HIGHLIGHT_DARK,
            )
        }

        HighlightType.Username,
        HighlightType.Custom,
        HighlightType.Reply,
        HighlightType.Badge,
        HighlightType.Notification,
        HighlightType.InlineWhisper,
        -> {
            BackgroundColors(
                light = COLOR_MENTION_HIGHLIGHT_LIGHT,
                dark = COLOR_MENTION_HIGHLIGHT_DARK,
            )
        }
    }

    private fun Set<Highlight>.toBackgroundColors(): BackgroundColors {
        val highlight =
            this.maxByOrNull { it.type.priority.value }
                ?: return BackgroundColors(Color.Transparent, Color.Transparent)

        // Explicitly colored announcements keep their color, custom colors only apply to default announcements
        if (highlight.type == HighlightType.Announcement && highlight.announcementColor != AnnouncementColor.Primary) {
            return getAnnouncementColors(highlight.announcementColor)
        }

        val customColor = highlight.customColor
        if (customColor != null && customColor !in DEFAULT_HIGHLIGHT_COLOR_INTS) {
            val color = Color(customColor)
            return BackgroundColors(color, color)
        }

        return getHighlightColors(highlight.type)
    }

    private fun getAnnouncementColors(color: AnnouncementColor): BackgroundColors = when (color) {
        AnnouncementColor.Primary -> getHighlightColors(HighlightType.Announcement)
        AnnouncementColor.Blue -> BackgroundColors(COLOR_ANNOUNCEMENT_BLUE_LIGHT, COLOR_ANNOUNCEMENT_BLUE_DARK)
        AnnouncementColor.Green -> BackgroundColors(COLOR_ANNOUNCEMENT_GREEN_LIGHT, COLOR_ANNOUNCEMENT_GREEN_DARK)
        AnnouncementColor.Orange -> BackgroundColors(COLOR_ANNOUNCEMENT_ORANGE_LIGHT, COLOR_ANNOUNCEMENT_ORANGE_DARK)
        AnnouncementColor.Purple -> BackgroundColors(COLOR_ANNOUNCEMENT_PURPLE_LIGHT, COLOR_ANNOUNCEMENT_PURPLE_DARK)
    }

    companion object {
        // Highlight colors - Light theme (all dark enough for white text, 80% opacity)
        private val COLOR_SUB_HIGHLIGHT_LIGHT = Color(0xCC7E57C2)
        private val COLOR_MENTION_HIGHLIGHT_LIGHT = Color(0xCCCF5050)
        private val COLOR_REDEMPTION_HIGHLIGHT_LIGHT = Color(0xCC458B93)
        private val COLOR_FIRST_MESSAGE_HIGHLIGHT_LIGHT = Color(0xCC558B2F)
        private val COLOR_ELEVATED_MESSAGE_HIGHLIGHT_LIGHT = Color(0xCCB08D2A)
        private val COLOR_WATCH_STREAK_HIGHLIGHT_LIGHT = Color(0xCC2979B7)
        private val COLOR_ANNOUNCEMENT_BLUE_LIGHT = Color(0xCC3363C0)
        private val COLOR_ANNOUNCEMENT_GREEN_LIGHT = Color(0xCC2E7D32)
        private val COLOR_ANNOUNCEMENT_ORANGE_LIGHT = Color(0xCCBF6516)
        private val COLOR_ANNOUNCEMENT_PURPLE_LIGHT = Color(0xCCAB3D9E)

        // Highlight colors - Dark theme (80% opacity)
        private val COLOR_SUB_HIGHLIGHT_DARK = Color(0xCC6A45A0)
        private val COLOR_MENTION_HIGHLIGHT_DARK = Color(0xCC8C3A3B)
        private val COLOR_REDEMPTION_HIGHLIGHT_DARK = Color(0xCC00606B)
        private val COLOR_FIRST_MESSAGE_HIGHLIGHT_DARK = Color(0xCC3A6600)
        private val COLOR_ELEVATED_MESSAGE_HIGHLIGHT_DARK = Color(0xCC6B5800)
        private val COLOR_WATCH_STREAK_HIGHLIGHT_DARK = Color(0xCC1A5C8A)
        private val COLOR_ANNOUNCEMENT_BLUE_DARK = Color(0xCC24488F)
        private val COLOR_ANNOUNCEMENT_GREEN_DARK = Color(0xCC1B5E20)
        private val COLOR_ANNOUNCEMENT_ORANGE_DARK = Color(0xCC8F4A0E)
        private val COLOR_ANNOUNCEMENT_PURPLE_DARK = Color(0xCC7E2C75)

        fun defaultHighlightColorInt(
            type: HighlightType,
            isDark: Boolean,
        ): Int = when (type) {
            HighlightType.Subscription, HighlightType.Announcement -> if (isDark) 0xCC6A45A0 else 0xCC7E57C2
            HighlightType.WatchStreak -> if (isDark) 0xCC1A5C8A else 0xCC2979B7
            HighlightType.Username, HighlightType.Custom, HighlightType.Reply, HighlightType.Notification, HighlightType.Badge, HighlightType.InlineWhisper -> if (isDark) 0xCC8C3A3B else 0xCCCF5050
            HighlightType.ChannelPointRedemption -> if (isDark) 0xCC00606B else 0xCC458B93
            HighlightType.FirstMessage -> if (isDark) 0xCC3A6600 else 0xCC558B2F
            HighlightType.ElevatedMessage -> if (isDark) 0xCC6B5800 else 0xCCB08D2A
        }.toInt()

        private val DEFAULT_HIGHLIGHT_COLOR_INTS =
            setOf(
                // Current defaults (80% opacity)
                0xCC7E57C2.toInt(), // sub light
                0xCC6A45A0.toInt(), // sub dark
                0xCCCF5050.toInt(), // mention light
                0xCC8C3A3B.toInt(), // mention dark
                0xCC458B93.toInt(), // redemption light
                0xCC00606B.toInt(), // redemption dark
                0xCC558B2F.toInt(), // first message light
                0xCC3A6600.toInt(), // first message dark
                0xCCB08D2A.toInt(), // elevated light
                0xCC6B5800.toInt(), // elevated dark
                0xCC2979B7.toInt(), // watch streak light
                0xCC1A5C8A.toInt(), // watch streak dark
                // Legacy defaults (fully opaque)
                0xFF7E57C2.toInt(), // sub light
                0xFF6A45A0.toInt(), // sub dark
                0xFFCF5050.toInt(), // mention light
                0xFF8C3A3B.toInt(), // mention dark
                0xFF458B93.toInt(), // redemption light
                0xFF00606B.toInt(), // redemption dark
                0xFF558B2F.toInt(), // first message light
                0xFF3A6600.toInt(), // first message dark
                0xFFB08D2A.toInt(), // elevated light
                0xFF6B5800.toInt(), // elevated dark
                0xFF2979B7.toInt(), // watch streak light
                0xFF1A5C8A.toInt(), // watch streak dark
                // Older legacy defaults
                0xFFD1C4E9.toInt(),
                0xFF543589.toInt(), // sub (v1)
                0xFFEF9A9A.toInt(),
                0xFF773031.toInt(), // mention (v1)
                0xFF93F1FF.toInt(),
                0xFF004F57.toInt(), // redemption (v1)
                0xFFC2F18D.toInt(),
                0xFF2D5000.toInt(), // first message (v1)
                0xFFFFE087.toInt(),
                0xFF574500.toInt(), // elevated (v1)
                0xFFB5A0D4.toInt(),
                0xFFE57373.toInt(), // sub/mention (v2 light)
                0xFFA8D8DF.toInt(),
                0xFFAED581.toInt(),
                0xFFEDD59A.toInt(), // redemption/first/elevated (v2 light)
            )

        // Twitch's 15 default username colors
        private val TWITCH_USERNAME_COLORS = intArrayOf(
            0xFFFF0000.toInt(), // Red
            0xFF0000FF.toInt(), // Blue
            0xFF00FF00.toInt(), // Green
            0xFFB22222.toInt(), // FireBrick
            0xFFFF7F50.toInt(), // Coral
            0xFF9ACD32.toInt(), // YellowGreen
            0xFFFF4500.toInt(), // OrangeRed
            0xFF2E8B57.toInt(), // SeaGreen
            0xFFDAA520.toInt(), // GoldenRod
            0xFFD2691E.toInt(), // Chocolate
            0xFF5F9EA0.toInt(), // CadetBlue
            0xFF1E90FF.toInt(), // DodgerBlue
            0xFFFF69B4.toInt(), // HotPink
            0xFF8A2BE2.toInt(), // BlueViolet
            0xFF00FF7F.toInt(), // SpringGreen
        )

        private fun getStableColor(userId: UserId): Int {
            val colorSeed = userId.value.toIntOrNull()
                ?: userId.value.sumOf { it.code }
            return TWITCH_USERNAME_COLORS[colorSeed % TWITCH_USERNAME_COLORS.size]
        }

        // Checkered background colors — 12% opacity overlay
        private const val CHECKERED_ALPHA = (255 * 0.12f).toInt()
        private val CHECKERED_LIGHT = Color(android.graphics.Color.argb(CHECKERED_ALPHA, 0, 0, 0))
        private val CHECKERED_DARK = Color(android.graphics.Color.argb(CHECKERED_ALPHA, 255, 255, 255))
    }
}

private fun ChatMessageUiState.hasSameHighlightBackground(other: ChatMessageUiState?): Boolean = other != null &&
    other.lightBackgroundColor == lightBackgroundColor &&
    other.darkBackgroundColor == darkBackgroundColor

fun MutableList<ChatMessageUiState>.applyHighlightLayout(
    showLineSeparator: Boolean,
    onItemUpdated: (index: Int, updated: ChatMessageUiState) -> Unit = { _, _ -> },
) {
    for (index in indices) {
        val message = this[index]
        val above = getOrNull(index - 1)
        val below = getOrNull(index + 1)
        val hasSameAbove = message.hasSameHighlightBackground(above)
        val hasSameBelow = message.hasSameHighlightBackground(below)

        val roundedTop = message.isHighlighted && !hasSameAbove
        val roundedBottom = message.isHighlighted && !hasSameBelow

        val isHighlightBoundary = (message.isHighlighted && !hasSameBelow) ||
            (below != null && below.isHighlighted && !below.hasSameHighlightBackground(message))
        val showDivider = showLineSeparator && below != null && !isHighlightBoundary

        val layoutChanged = message.roundedTopCorners != roundedTop ||
            message.roundedBottomCorners != roundedBottom ||
            message.showDividerBelow != showDivider
        if (layoutChanged) {
            val updated = message.withLayout(roundedTop, roundedBottom, showDivider)
            this[index] = updated
            onItemUpdated(index, updated)
        }
    }
}

private fun ChatMessageUiState.withLayout(
    roundedTopCorners: Boolean,
    roundedBottomCorners: Boolean,
    showDividerBelow: Boolean,
): ChatMessageUiState = when (this) {
    is ChatMessageUiState.PrivMessageUi -> copy(roundedTopCorners = roundedTopCorners, roundedBottomCorners = roundedBottomCorners, showDividerBelow = showDividerBelow)
    is ChatMessageUiState.SystemMessageUi -> copy(roundedTopCorners = roundedTopCorners, roundedBottomCorners = roundedBottomCorners, showDividerBelow = showDividerBelow)
    is ChatMessageUiState.NoticeMessageUi -> copy(roundedTopCorners = roundedTopCorners, roundedBottomCorners = roundedBottomCorners, showDividerBelow = showDividerBelow)
    is ChatMessageUiState.UserNoticeMessageUi -> copy(roundedTopCorners = roundedTopCorners, roundedBottomCorners = roundedBottomCorners, showDividerBelow = showDividerBelow)
    is ChatMessageUiState.ModerationMessageUi -> copy(roundedTopCorners = roundedTopCorners, roundedBottomCorners = roundedBottomCorners, showDividerBelow = showDividerBelow)
    is ChatMessageUiState.PointRedemptionMessageUi -> copy(roundedTopCorners = roundedTopCorners, roundedBottomCorners = roundedBottomCorners, showDividerBelow = showDividerBelow)
    is ChatMessageUiState.DateSeparatorUi -> copy(roundedTopCorners = roundedTopCorners, roundedBottomCorners = roundedBottomCorners, showDividerBelow = showDividerBelow)
    is ChatMessageUiState.AutomodMessageUi -> copy(roundedTopCorners = roundedTopCorners, roundedBottomCorners = roundedBottomCorners, showDividerBelow = showDividerBelow)
    is ChatMessageUiState.WhisperMessageUi -> copy(roundedTopCorners = roundedTopCorners, roundedBottomCorners = roundedBottomCorners, showDividerBelow = showDividerBelow)
}
