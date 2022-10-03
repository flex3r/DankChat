package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.badge.BadgeType
import com.flxrs.dankchat.data.twitch.emote.EmoteManager

sealed class Message {
    abstract val id: String
    abstract val timestamp: Long
    abstract val highlights: List<Highlight>

    companion object {
        const val DEFAULT_COLOR = "#717171"
        fun parse(message: IrcMessage, emoteManager: EmoteManager): Message? = with(message) {
            return when (command) {
                "PRIVMSG"    -> PrivMessage.parsePrivMessage(message, emoteManager)
                "NOTICE"     -> NoticeMessage.parseNotice(message)
                "USERNOTICE" -> UserNoticeMessage.parseUserNotice(message, emoteManager)
                else         -> null
            }
        }

        fun parseBadges(emoteManager: EmoteManager, badgeTags: String?, badgeInfoTag: String?, channel: String = "", userId: String? = null): List<Badge> {
            val badgeInfos = badgeInfoTag
                ?.parseTagList()
                ?.associate { it.key to it.value }
                .orEmpty()

            val badges = badgeTags
                ?.parseTagList()
                ?.mapNotNull { (badgeKey, badgeValue, tag) ->
                    val badgeInfo = badgeInfos[badgeKey]

                    val globalBadgeUrl = emoteManager.getGlobalBadgeUrl(badgeKey, badgeValue)
                    val channelBadgeUrl = emoteManager.getChannelBadgeUrl(channel, badgeKey, badgeValue)
                    val ffzModBadgeUrl = emoteManager.getFfzModBadgeUrl(channel)
                    val ffzVipBadgeUrl = emoteManager.getFfzVipBadgeUrl(channel)

                    val title = emoteManager.getBadgeTitle(channel, badgeKey, badgeValue)
                    val type = BadgeType.parseFromBadgeId(badgeKey)
                    when {
                        badgeKey.startsWith("moderator") && ffzModBadgeUrl != null -> Badge.FFZModBadge(
                            title = title,
                            badgeTag = tag,
                            badgeInfo = badgeInfo,
                            url = ffzModBadgeUrl,
                            type = type
                        )

                        badgeKey.startsWith("vip") && ffzVipBadgeUrl != null       -> Badge.FFZVipBadge(
                            title = title,
                            badgeTag = tag,
                            badgeInfo = badgeInfo,
                            url = ffzVipBadgeUrl,
                            type = type
                        )

                        (badgeKey.startsWith("subscriber") || badgeKey.startsWith("bits"))
                                && channelBadgeUrl != null                         -> Badge.ChannelBadge(
                            title = title,
                            badgeTag = tag,
                            badgeInfo = badgeInfo,
                            url = channelBadgeUrl,
                            type = type
                        )

                        else                                                       -> globalBadgeUrl?.let { Badge.GlobalBadge(title, tag, badgeInfo, it, type) }
                    }
                }.orEmpty()

            userId ?: return badges
            return when (val badge = emoteManager.getDankChatBadgeTitleAndUrl(userId)) {
                null -> badges
                else -> {
                    val (title, url) = badge
                    badges + Badge.DankChatBadge(title = title, badgeTag = null, badgeInfo = null, url = url, type = BadgeType.DankChat)
                }
            }
        }

        data class TagListEntry(val key: String, val value: String, val tag: String)

        private fun String.parseTagList(): List<TagListEntry> = split(',')
            .mapNotNull {
                if (!it.contains('/')) {
                    return@mapNotNull null
                }

                val key = it.substringBefore('/')
                val value = it.substringAfter('/')
                TagListEntry(key, value, it)
            }
    }
}


