package com.flxrs.dankchat.data.repo.emote

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.bttv.dto.BTTVChannelDto
import com.flxrs.dankchat.data.api.bttv.dto.BTTVEmoteDto
import com.flxrs.dankchat.data.api.bttv.dto.BTTVGlobalEmoteDto
import com.flxrs.dankchat.data.api.dankchat.DankChatApiClient
import com.flxrs.dankchat.data.api.dankchat.dto.DankChatBadgeDto
import com.flxrs.dankchat.data.api.dankchat.dto.DankChatEmoteDto
import com.flxrs.dankchat.data.api.ffz.dto.FFZChannelDto
import com.flxrs.dankchat.data.api.ffz.dto.FFZEmoteDto
import com.flxrs.dankchat.data.api.ffz.dto.FFZGlobalDto
import com.flxrs.dankchat.data.api.seventv.SevenTVUserDetails
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteFileDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteSetDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVUserConnection
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVUserDto
import com.flxrs.dankchat.data.api.seventv.eventapi.SevenTVEventMessage
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.badge.BadgeSet
import com.flxrs.dankchat.data.twitch.badge.BadgeType
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmoteType
import com.flxrs.dankchat.data.twitch.emote.EmoteType
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.data.twitch.emote.toChatMessageEmoteType
import com.flxrs.dankchat.data.twitch.message.EmoteWithPositions
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.MultiCallback
import com.flxrs.dankchat.utils.extensions.appendSpacesBetweenEmojiGroup
import com.flxrs.dankchat.utils.extensions.removeDuplicateWhitespace
import com.flxrs.dankchat.utils.extensions.supplementaryCodePointPositions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmoteRepository @Inject constructor(
    private val dankChatApiClient: DankChatApiClient,
    private val preferences: DankChatPreferenceStore,
) {
    private val ffzModBadges = ConcurrentHashMap<UserName, String?>()
    private val ffzVipBadges = ConcurrentHashMap<UserName, String?>()
    private val channelBadges = ConcurrentHashMap<UserName, Map<String, BadgeSet>>()
    private val globalBadges = ConcurrentHashMap<String, BadgeSet>()
    private val dankChatBadges = CopyOnWriteArrayList<DankChatBadgeDto>()

    private val sevenTvChannelDetails = ConcurrentHashMap<UserName, SevenTVUserDetails>()

    private val emotes = ConcurrentHashMap<UserName, MutableStateFlow<Emotes>>()

    val badgeCache = LruCache<String, Drawable>(64)
    val layerCache = LruCache<String, LayerDrawable>(256)
    val gifCallback = MultiCallback()

    fun getEmotes(channel: UserName): StateFlow<Emotes> = emotes.getOrPut(channel) { MutableStateFlow(Emotes()) }
    fun createFlowsIfNecessary(channels: List<UserName>) {
        channels.forEach { emotes.putIfAbsent(it, MutableStateFlow(Emotes())) }
    }

    fun removeChannel(channel: UserName) {
        emotes.remove(channel)
    }

    fun parse3rdPartyEmotes(message: String, channel: UserName, withTwitch: Boolean = false): List<ChatMessageEmote> {
        val splits = message.split(WHITESPACE_REGEX)
        val available = emotes[channel]?.value ?: return emptyList()

        return buildList {
            if (withTwitch) {
                addAll(available.twitchEmotes.flatMap { parseMessageForEmote(it, splits) })
            }

            if (channel != WhisperMessage.WHISPER_CHANNEL) {
                addAll(available.ffzChannelEmotes.flatMap { parseMessageForEmote(it, splits) })
                addAll(available.bttvChannelEmotes.flatMap { parseMessageForEmote(it, splits) })
                addAll(available.sevenTvChannelEmotes.flatMap { parseMessageForEmote(it, splits) })
            }

            addAll(available.ffzGlobalEmotes.flatMap { parseMessageForEmote(it, splits) })
            addAll(available.bttvGlobalEmotes.flatMap { parseMessageForEmote(it, splits) })
            addAll(available.sevenTvGlobalEmotes.flatMap { parseMessageForEmote(it, splits) })
        }.distinctBy { it.code to it.position }
    }

    fun parseEmotesAndBadges(message: Message): Message {
        val replyMentionOffset = (message as? PrivMessage)?.replyMentionOffset ?: 0
        val emoteData = message.emoteData ?: return message
        val (messageString, channel, emotesWithPositions) = emoteData

        val withEmojiFix = messageString.replace(
            ChatRepository.ESCAPE_TAG_REGEX,
            ChatRepository.ZERO_WIDTH_JOINER
        )
        val (duplicateSpaceAdjustedMessage, removedSpaces) = withEmojiFix.removeDuplicateWhitespace()
        val (appendedSpaceAdjustedMessage, appendedSpaces) = duplicateSpaceAdjustedMessage.appendSpacesBetweenEmojiGroup()

        val twitchEmotes = parseTwitchEmotes(emotesWithPositions, appendedSpaceAdjustedMessage, appendedSpaces, removedSpaces, replyMentionOffset)
        val thirdPartyEmotes = parse3rdPartyEmotes(appendedSpaceAdjustedMessage, channel).filterNot { e -> twitchEmotes.any { it.code == e.code } }
        val emotes = (twitchEmotes + thirdPartyEmotes)

        val (adjustedMessage, adjustedEmotes) = adjustOverlayEmotes(appendedSpaceAdjustedMessage, emotes)
        val messageWithEmotes = when (message) {
            is PrivMessage       -> message.copy(message = adjustedMessage, emotes = adjustedEmotes)
            is WhisperMessage    -> message.copy(message = adjustedMessage, emotes = adjustedEmotes)
            is UserNoticeMessage -> message.copy(
                childMessage = message.childMessage?.copy(
                    message = adjustedMessage,
                    emotes = adjustedEmotes
                )
            )

            else                 -> message
        }

        return parseBadges(messageWithEmotes)
    }

    private fun parseBadges(message: Message): Message {
        val badgeData = message.badgeData ?: return message
        val (userId, channel, badgeTag, badgeInfoTag) = badgeData

        val badgeInfos = badgeInfoTag
            ?.parseTagList()
            ?.associate { it.key to it.value }
            .orEmpty()

        val badges = badgeTag
            ?.parseTagList()
            ?.mapNotNull { (badgeKey, badgeValue, tag) ->
                val badgeInfo = badgeInfos[badgeKey]

                val globalBadgeUrl = getGlobalBadgeUrl(badgeKey, badgeValue)
                val channelBadgeUrl = getChannelBadgeUrl(channel, badgeKey, badgeValue)
                val ffzModBadgeUrl = getFfzModBadgeUrl(channel)
                val ffzVipBadgeUrl = getFfzVipBadgeUrl(channel)

                val title = getBadgeTitle(channel, badgeKey, badgeValue)
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

        val badgesWithDankChatBadge = buildList {
            addAll(badges)
            val badge = getDankChatBadgeTitleAndUrl(userId)
            if (badge != null) {
                add(Badge.DankChatBadge(title = badge.first, badgeTag = null, badgeInfo = null, url = badge.second, type = BadgeType.DankChat))
            }
        }

        return when (message) {
            is PrivMessage       -> message.copy(badges = badgesWithDankChatBadge)
            is WhisperMessage    -> message.copy(badges = badgesWithDankChatBadge)
            is UserNoticeMessage -> message.copy(
                childMessage = message.childMessage?.copy(badges = badgesWithDankChatBadge)
            )

            else                 -> message
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

    private fun getChannelBadgeUrl(channel: UserName?, set: String, version: String) = channel?.let { channelBadges[channel]?.get(set)?.versions?.get(version)?.imageUrlHigh }

    private fun getGlobalBadgeUrl(set: String, version: String) = globalBadges[set]?.versions?.get(version)?.imageUrlHigh

    private fun getBadgeTitle(channel: UserName?, set: String, version: String): String? {
        return channel?.let { channelBadges[channel]?.get(set)?.versions?.get(version)?.title }
            ?: globalBadges[set]?.versions?.get(version)?.title
    }

    private fun getFfzModBadgeUrl(channel: UserName?): String? = channel?.let { ffzModBadges[channel] }

    private fun getFfzVipBadgeUrl(channel: UserName?): String? = channel?.let { ffzVipBadges[channel] }

    private fun getDankChatBadgeTitleAndUrl(userId: UserId?): Pair<String, String>? = dankChatBadges.find { it.users.any { id -> id == userId } }?.let { it.type to it.url }

    fun setChannelBadges(channel: UserName, badges: Map<String, BadgeSet>) {
        channelBadges[channel] = badges
    }

    fun setGlobalBadges(badges: Map<String, BadgeSet>) {
        globalBadges.putAll(badges)
    }

    fun setDankChatBadges(dto: List<DankChatBadgeDto>) {
        dankChatBadges.addAll(dto)
    }

    fun getChannelForSevenTVEmoteSet(emoteSetId: String): UserName? = sevenTvChannelDetails
        .entries
        .find { (_, details) -> details.activeEmoteSetId == emoteSetId }
        ?.key

    fun getSevenTVUserDetails(channel: UserName): SevenTVUserDetails? = sevenTvChannelDetails[channel]

    suspend fun loadUserStateEmotes(globalEmoteSetIds: List<String>, followerEmoteSetIds: Map<UserName, List<String>>) = withContext(Dispatchers.Default) {
        val combined = (globalEmoteSetIds + followerEmoteSetIds.values.flatten()).distinct()
        val sets = dankChatApiClient.getUserSets(combined)
            .getOrNull()
            .orEmpty()

        val twitchEmotes = sets.flatMap { emoteSet ->
            val type = when (val set = emoteSet.id) {
                "0", "42" -> EmoteType.GlobalTwitchEmote // 42 == monkey emote set, move them to the global emote section
                else      -> {
                    followerEmoteSetIds.entries
                        .find { (_, sets) ->
                            set in sets
                        }
                        ?.let { EmoteType.ChannelTwitchFollowerEmote(it.key) }
                        ?: emoteSet.channelName.twitchEmoteType
                }
            }
            emoteSet.emotes.mapToGenericEmotes(type)
        }

        emotes.forEach { (channel, flow) ->
            flow.update {
                it.copy(
                    twitchEmotes = twitchEmotes.filterNot { emote -> emote.emoteType is EmoteType.ChannelTwitchFollowerEmote && emote.emoteType.channel != channel }
                )
            }
        }
    }

    suspend fun setFFZEmotes(channel: UserName, ffzResult: FFZChannelDto) = withContext(Dispatchers.Default) {
        val ffzEmotes = ffzResult.sets
            .flatMap { set ->
                set.value.emotes.mapNotNull {
                    parseFFZEmote(it, channel)
                }
            }
        emotes[channel]?.update {
            it.copy(ffzChannelEmotes = ffzEmotes)
        }
        ffzResult.room.modBadgeUrls?.let {
            val url = it["4"] ?: it["2"] ?: it["1"]
            ffzModBadges[channel] = url?.withLeadingHttps
        }
        ffzResult.room.vipBadgeUrls?.let {
            val url = it["4"] ?: it["2"] ?: it["1"]
            ffzVipBadges[channel] = url?.withLeadingHttps
        }
    }

    suspend fun setFFZGlobalEmotes(ffzResult: FFZGlobalDto) = withContext(Dispatchers.Default) {
        val ffzGlobalEmotes = ffzResult.sets
            .filter { it.key in ffzResult.defaultSets }
            .flatMap { (_, emoteSet) ->
                emoteSet.emotes.mapNotNull { emote ->
                    parseFFZEmote(emote, channel = null)
                }
            }
        emotes.values.forEach { flow ->
            flow.update {
                it.copy(ffzGlobalEmotes = ffzGlobalEmotes)
            }
        }
    }

    suspend fun setBTTVEmotes(channel: UserName, channelDisplayName: DisplayName, bttvResult: BTTVChannelDto) = withContext(Dispatchers.Default) {
        val bttvEmotes = (bttvResult.emotes + bttvResult.sharedEmotes).map { parseBTTVEmote(it, channelDisplayName) }
        emotes[channel]?.update {
            it.copy(bttvChannelEmotes = bttvEmotes)
        }
    }

    suspend fun setBTTVGlobalEmotes(globalEmotes: List<BTTVGlobalEmoteDto>) = withContext(Dispatchers.Default) {
        val bttvGlobalEmotes = globalEmotes.map { parseBTTVGlobalEmote(it) }
        emotes.values.forEach { flow ->
            flow.update {
                it.copy(bttvGlobalEmotes = bttvGlobalEmotes)
            }
        }
    }

    suspend fun setSevenTVEmotes(channel: UserName, userDto: SevenTVUserDto) = withContext(Dispatchers.Default) {
        val emoteSetId = userDto.emoteSet?.id ?: return@withContext
        val emoteList = userDto.emoteSet.emotes.orEmpty()

        sevenTvChannelDetails[channel] = SevenTVUserDetails(
            id = userDto.user.id,
            activeEmoteSetId = emoteSetId,
            connectionIndex = userDto.user.connections.indexOfFirst { it.platform == SevenTVUserConnection.twitch }
        )
        val sevenTvEmotes = emoteList
            .filterUnlistedIfEnabled()
            .mapNotNull { emote ->
                parseSevenTVEmote(emote, EmoteType.ChannelSevenTVEmote(emote.data?.owner?.displayName, emote.data?.baseName?.takeIf { emote.name != it }))
            }

        emotes[channel]?.update {
            it.copy(sevenTvChannelEmotes = sevenTvEmotes)
        }
    }

    suspend fun setSevenTVEmoteSet(channel: UserName, emoteSet: SevenTVEmoteSetDto) = withContext(Dispatchers.Default) {
        sevenTvChannelDetails[channel]?.let { details ->
            sevenTvChannelDetails[channel] = details.copy(activeEmoteSetId = emoteSet.id)
        }

        val sevenTvEmotes = emoteSet.emotes
            .orEmpty()
            .filterUnlistedIfEnabled()
            .mapNotNull { emote ->
                parseSevenTVEmote(emote, EmoteType.ChannelSevenTVEmote(emote.data?.owner?.displayName, emote.data?.baseName?.takeIf { emote.name != it }))
            }

        emotes[channel]?.update {
            it.copy(sevenTvChannelEmotes = sevenTvEmotes)
        }
    }

    suspend fun updateSevenTVEmotes(channel: UserName, event: SevenTVEventMessage.EmoteSetUpdated) = withContext(Dispatchers.Default) {
        val addedEmotes = event.added
            .filterUnlistedIfEnabled()
            .mapNotNull { emote ->
                parseSevenTVEmote(emote, EmoteType.ChannelSevenTVEmote(emote.data?.owner?.displayName, emote.data?.baseName?.takeIf { emote.name != it }))
            }

        emotes[channel]?.update { emotes ->
            val updated = emotes.sevenTvChannelEmotes.mapNotNull { emote ->

                if (event.removed.any { emote.id == it.id }) {
                    null
                } else {
                    event.updated.find { emote.id == it.id }?.let { update ->
                        val mapNewBaseName = { oldBase: String? -> (oldBase ?: emote.code).takeIf { it != update.name } }
                        val newType = when (emote.emoteType) {
                            is EmoteType.ChannelSevenTVEmote -> emote.emoteType.copy(baseName = mapNewBaseName(emote.emoteType.baseName))
                            is EmoteType.GlobalSevenTVEmote  -> emote.emoteType.copy(baseName = mapNewBaseName(emote.emoteType.baseName))
                            else                             -> emote.emoteType
                        }
                        emote.copy(code = update.name, emoteType = newType)
                    } ?: emote
                }
            }
            emotes.copy(sevenTvChannelEmotes = updated + addedEmotes)
        }
    }

    suspend fun setSevenTVGlobalEmotes(sevenTvResult: List<SevenTVEmoteDto>) = withContext(Dispatchers.Default) {
        if (sevenTvResult.isEmpty()) return@withContext

        val sevenTvGlobalEmotes = sevenTvResult
            .filterUnlistedIfEnabled()
            .mapNotNull { emote ->
                parseSevenTVEmote(emote, EmoteType.GlobalSevenTVEmote(emote.data?.owner?.displayName, emote.data?.baseName?.takeIf { emote.name != it }))
            }

        emotes.values.forEach { flow ->
            flow.update {
                it.copy(sevenTvGlobalEmotes = sevenTvGlobalEmotes)
            }
        }
    }

    private val UserName?.twitchEmoteType: EmoteType
        get() = when {
            this == null || isGlobalTwitchChannel -> EmoteType.GlobalTwitchEmote
            else                                  -> EmoteType.ChannelTwitchEmote(this)
        }

    private val UserName.isGlobalTwitchChannel: Boolean
        get() = value.equals("qa_TW_Partner", ignoreCase = true) || value.equals("Twitch", ignoreCase = true)

    private fun List<DankChatEmoteDto>?.mapToGenericEmotes(type: EmoteType): List<GenericEmote> = this?.map { (name, id) ->
        val code = when (type) {
            is EmoteType.GlobalTwitchEmote -> EMOTE_REPLACEMENTS[name] ?: name
            else                           -> name
        }
        GenericEmote(
            code = code,
            url = TWITCH_EMOTE_TEMPLATE.format(id, TWITCH_EMOTE_SIZE),
            lowResUrl = TWITCH_EMOTE_TEMPLATE.format(id, TWITCH_LOW_RES_EMOTE_SIZE),
            id = id,
            scale = 1,
            emoteType = type
        )
    }.orEmpty()

    @VisibleForTesting
    fun adjustOverlayEmotes(message: String, emotes: List<ChatMessageEmote>): Pair<String, List<ChatMessageEmote>> {
        var adjustedMessage = message
        val adjustedEmotes = emotes.sortedBy { it.position.first }.toMutableList()

        for (i in adjustedEmotes.lastIndex downTo 0) {
            val emote = adjustedEmotes[i]

            if (emote.isOverlayEmote) {
                var foundEmote = false
                var distanceToRegularEmote = 1 // initial space
                // first, iterate over previous emotes until a regular emote is found
                for (j in i - 1 downTo 0) {
                    val previousEmote = adjustedEmotes[j]
                    if (previousEmote.isOverlayEmote) {
                        distanceToRegularEmote += previousEmote.code.length + 1 // emote code + space
                        continue
                    }

                    val actualDistanceToRegularEmote = emote.position.first - previousEmote.position.last

                    // The "distance" between the found non-overlay emote and the current overlay emote does not match the expected, valid distance
                    // This means, that there are non-emote "words" in-between and we should not adjust this overlay emote
                    // Example: FeelsDankMan asd cvHazmat RainTime
                    // actualDistanceToRegularEmote = 14 != distanceToRegularEmote = 10 -> break
                    if (actualDistanceToRegularEmote != distanceToRegularEmote) {
                        break
                    }

                    adjustedMessage = when (emote.position.last) {
                        adjustedMessage.length -> adjustedMessage.substring(0, emote.position.first)
                        else                   -> adjustedMessage.removeRange(emote.position)
                    }
                    adjustedEmotes[i] = emote.copy(position = previousEmote.position)
                    foundEmote = true

                    break
                }

                if (foundEmote) {
                    // iterate forward to fix future emote positions
                    for (k in i + 1..adjustedEmotes.lastIndex) {
                        val nextEmote = adjustedEmotes[k]
                        if (emote.position.first >= nextEmote.position.first) {
                            continue
                        }

                        val first = nextEmote.position.first - emote.code.length - 1
                        val last = nextEmote.position.last - emote.code.length - 1
                        adjustedEmotes[k] = nextEmote.copy(position = first..last)
                    }
                }
            }
        }

        return adjustedMessage to adjustedEmotes
    }

    private fun parseMessageForEmote(emote: GenericEmote, words: List<String>): List<ChatMessageEmote> {
        var currentPosition = 0
        return buildList {
            words.forEach { word ->
                if (emote.code == word) {
                    this += ChatMessageEmote(
                        position = currentPosition..currentPosition + word.length,
                        url = emote.url,
                        id = emote.id,
                        code = emote.code,
                        scale = emote.scale,
                        type = emote.emoteType.toChatMessageEmoteType() ?: ChatMessageEmoteType.TwitchEmote,
                        isOverlayEmote = emote.isOverlayEmote
                    )
                }
                currentPosition += word.length + 1
            }
        }
    }

    private fun parseTwitchEmotes(
        emotesWithPositions: List<EmoteWithPositions>,
        message: String,
        appendedSpaces: List<Int>,
        removedSpaces: List<Int>,
        replyMentionOffset: Int,
    ): List<ChatMessageEmote> {
        // Characters with supplementary codepoints have two chars and need to be considered into emote positioning
        val supplementaryCodePointPositions = message.supplementaryCodePointPositions
        return emotesWithPositions.flatMap { (id, positions) ->
            positions.map { range ->
                val removedSpaceExtra = removedSpaces.count { it < range.first }
                val unicodeExtra = supplementaryCodePointPositions.count { it < range.first - removedSpaceExtra }
                val spaceExtra = appendedSpaces.count { it < range.first + unicodeExtra }
                val fixedStart = range.first + unicodeExtra + spaceExtra - removedSpaceExtra - replyMentionOffset
                val fixedEnd = range.last + unicodeExtra + spaceExtra - removedSpaceExtra - replyMentionOffset

                // be extra safe in case twitch sends invalid emote ranges :)
                val fixedPos = fixedStart.coerceAtLeast(minimumValue = 0)..(fixedEnd + 1).coerceAtMost(message.length)
                val code = message.substring(fixedPos.first, fixedPos.last)
                ChatMessageEmote(
                    position = fixedPos,
                    url = TWITCH_EMOTE_TEMPLATE.format(id, TWITCH_EMOTE_SIZE),
                    id = id,
                    code = code,
                    scale = 1,
                    type = ChatMessageEmoteType.TwitchEmote,
                    isTwitch = true
                )
            }
        }
    }

    private fun parseBTTVEmote(emote: BTTVEmoteDto, channelDisplayName: DisplayName): GenericEmote {
        val name = emote.code
        val id = emote.id
        val url = BTTV_EMOTE_TEMPLATE.format(id, BTTV_EMOTE_SIZE)
        val lowResUrl = BTTV_EMOTE_TEMPLATE.format(id, BTTV_LOW_RES_EMOTE_SIZE)
        return GenericEmote(
            code = name,
            url = url,
            lowResUrl = lowResUrl,
            id = id,
            scale = 1,
            emoteType = EmoteType.ChannelBTTVEmote(emote.user?.displayName ?: channelDisplayName, isShared = emote.user != null),
            isOverlayEmote = name in OVERLAY_EMOTES,
        )
    }

    private fun parseBTTVGlobalEmote(emote: BTTVGlobalEmoteDto): GenericEmote {
        val name = emote.code
        val id = emote.id
        val url = BTTV_EMOTE_TEMPLATE.format(id, BTTV_EMOTE_SIZE)
        val lowResUrl = BTTV_EMOTE_TEMPLATE.format(id, BTTV_LOW_RES_EMOTE_SIZE)
        return GenericEmote(
            code = name,
            url = url,
            lowResUrl = lowResUrl,
            id = id,
            scale = 1,
            emoteType = EmoteType.GlobalBTTVEmote,
            isOverlayEmote = name in OVERLAY_EMOTES,
        )
    }

    private fun parseFFZEmote(emote: FFZEmoteDto, channel: UserName?): GenericEmote? {
        val name = emote.name
        val id = emote.id
        val urlMap = emote.animated
            ?.mapValues { (_, url) -> url.takeIf { SUPPORTS_WEBP } ?: "$url.gif" }
            ?: emote.urls

        val (scale, url) = when {
            urlMap["4"] != null -> 1 to urlMap.getValue("4")
            urlMap["2"] != null -> 2 to urlMap.getValue("2")
            else                -> 4 to urlMap["1"]
        }
        url ?: return null
        val lowResUrl = urlMap["2"] ?: urlMap["1"] ?: return null
        val type = when (channel) {
            null -> EmoteType.GlobalFFZEmote(emote.owner?.displayName)
            else -> EmoteType.ChannelFFZEmote(emote.owner?.displayName)
        }
        return GenericEmote(name, url.withLeadingHttps, lowResUrl.withLeadingHttps, "$id", scale, type)
    }

    private fun parseSevenTVEmote(emote: SevenTVEmoteDto, type: EmoteType): GenericEmote? {
        val data = emote.data ?: return null
        if (data.isTwitchDisallowed) {
            return null
        }

        val base = "${data.host.url}/".withLeadingHttps
        val urls = data.host.files
            .filter { it.format == "WEBP" }
            .associate {
                val size = it.name.substringBeforeLast('.')
                size to it.emoteUrlWithFallback(base, size, data.animated)
            }

        return GenericEmote(
            code = emote.name,
            url = urls["4x"] ?: return null,
            lowResUrl = urls["2x"] ?: urls["1x"] ?: return null,
            id = emote.id,
            scale = 1,
            emoteType = type,
            isOverlayEmote = emote.isZeroWidth,
        )
    }

    private fun SevenTVEmoteFileDto.emoteUrlWithFallback(base: String, size: String, animated: Boolean): String {
        return when {
            animated && !SUPPORTS_WEBP -> "$base$size.gif"
            else                       -> "$base$name"
        }
    }

    private fun List<SevenTVEmoteDto>.filterUnlistedIfEnabled(): List<SevenTVEmoteDto> = when {
        preferences.unlistedSevenTVEmotesEnabled -> this
        else                                     -> filter { it.data?.listed == true }
    }

    private val String.withLeadingHttps: String
        get() = when {
            startsWith(prefix = "https:") -> this
            else                          -> "https:$this"
        }

    companion object {
        private val SUPPORTS_WEBP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        fun Badge.cacheKey(baseHeight: Int): String = "$url-$baseHeight"
        fun List<ChatMessageEmote>.cacheKey(baseHeight: Int): String = joinToString(separator = "-") { it.id } + "-$baseHeight"

        private val TAG = EmoteRepository::class.java.simpleName

        private const val TWITCH_EMOTE_TEMPLATE = "https://static-cdn.jtvnw.net/emoticons/v2/%s/default/dark/%s"
        private const val TWITCH_EMOTE_SIZE = "3.0"
        private const val TWITCH_LOW_RES_EMOTE_SIZE = "2.0"

        private const val BTTV_EMOTE_TEMPLATE = "https://cdn.betterttv.net/emote/%s/%s"
        private const val BTTV_EMOTE_SIZE = "3x"
        private const val BTTV_LOW_RES_EMOTE_SIZE = "2x"

        private val WHITESPACE_REGEX = "\\s".toRegex()
        private val EMOTE_REPLACEMENTS = mapOf(
            "[oO](_|\\.)[oO]" to "O_o",
            "\\&lt\\;3" to "<3",
            "\\:-?(p|P)" to ":P",
            "\\:-?[z|Z|\\|]" to ":Z",
            "\\:-?\\)" to ":)",
            "\\;-?(p|P)" to ";P",
            "R-?\\)" to "R)",
            "\\&gt\\;\\(" to ">(",
            "\\:-?(o|O)" to ":O",
            "\\:-?[\\\\/]" to ":/",
            "\\:-?\\(" to ":(",
            "\\:-?D" to ":D",
            "\\;-?\\)" to ";)",
            "B-?\\)" to "B)",
            "#-?[\\/]" to "#/",
            ":-?(?:7|L)" to ":7",
            "\\&lt\\;\\]" to "<]",
            "\\:-?(S|s)" to ":s",
            "\\:\\&gt\\;" to ":>"
        )
        private val OVERLAY_EMOTES = listOf(
            "SoSnowy", "IceCold", "SantaHat", "TopHat",
            "ReinDeer", "CandyCane", "cvMask", "cvHazmat",
        )
    }
}
