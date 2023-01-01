package com.flxrs.dankchat.data.repo

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.LruCache
import androidx.annotation.VisibleForTesting
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
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteVisibility
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.badge.BadgeSet
import com.flxrs.dankchat.data.twitch.badge.BadgeType
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.emote.EmoteType
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.appendSpacesBetweenEmojiGroup
import com.flxrs.dankchat.utils.extensions.removeDuplicateWhitespace
import com.flxrs.dankchat.utils.extensions.supplementaryCodePointPositions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.MultiCallback
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmoteRepository @Inject constructor(
    private val dankChatApiClient: DankChatApiClient,
    private val preferences: DankChatPreferenceStore
) {
    private val twitchEmotes = ConcurrentHashMap<String, GenericEmote>()

    private val ffzEmotes = ConcurrentHashMap<UserName, Map<String, GenericEmote>>()
    private val globalFFZEmotes = ConcurrentHashMap<String, GenericEmote>()

    private val bttvEmotes = ConcurrentHashMap<UserName, Map<String, GenericEmote>>()
    private val globalBttvEmotes = ConcurrentHashMap<String, GenericEmote>()

    private val sevenTVEmotes = ConcurrentHashMap<UserName, Map<String, GenericEmote>>()
    private val globalSevenTVEmotes = ConcurrentHashMap<String, GenericEmote>()

    private val ffzModBadges = ConcurrentHashMap<UserName, String>()
    private val ffzVipBadges = ConcurrentHashMap<UserName, String>()
    private val channelBadges = ConcurrentHashMap<UserName, Map<String, BadgeSet>>()
    private val globalBadges = ConcurrentHashMap<String, BadgeSet>()
    private val dankChatBadges = CopyOnWriteArrayList<DankChatBadgeDto>()

    val badgeCache = LruCache<String, Drawable>(64)
    val layerCache = LruCache<String, LayerDrawable>(256)
    val gifCallback = MultiCallback(true)

    fun parse3rdPartyEmotes(message: String, channel: UserName?, withTwitch: Boolean = false): List<ChatMessageEmote> {
        val splits = message.split(WHITESPACE_REGEX)
        val emotes = mutableListOf<ChatMessageEmote>()

        if (withTwitch) twitchEmotes.forEach { emotes.addAll(parseMessageForEmote(it.value, splits)) }
        ffzEmotes[channel]?.forEach { emotes.addAll(parseMessageForEmote(it.value, splits, emotes)) }
        bttvEmotes[channel]?.forEach { emotes.addAll(parseMessageForEmote(it.value, splits, emotes)) }
        sevenTVEmotes[channel]?.forEach { emotes.addAll(parseMessageForEmote(it.value, splits, emotes)) }
        globalFFZEmotes.forEach { emotes.addAll(parseMessageForEmote(it.value, splits, emotes)) }
        globalBttvEmotes.forEach { emotes.addAll(parseMessageForEmote(it.value, splits, emotes)) }
        globalSevenTVEmotes.forEach { emotes.addAll(parseMessageForEmote(it.value, splits, emotes)) }

        return emotes
    }

    fun parseEmotesAndBadges(message: Message): Message {
        val emoteData = message.emoteData ?: return message
        val (messageString, channel, emoteTag) = emoteData

        val withEmojiFix = messageString.replace(
            ChatRepository.ESCAPE_TAG_REGEX,
            ChatRepository.ZERO_WIDTH_JOINER
        )
        val (duplicateSpaceAdjustedMessage, removedSpaces) = withEmojiFix.removeDuplicateWhitespace()
        val (appendedSpaceAdjustedMessage, appendedSpaces) = duplicateSpaceAdjustedMessage.appendSpacesBetweenEmojiGroup()

        val twitchEmotes = parseTwitchEmotes(emoteTag, appendedSpaceAdjustedMessage, appendedSpaces, removedSpaces)
        val thirdPartyEmotes = parse3rdPartyEmotes(appendedSpaceAdjustedMessage, channel).filterNot { e -> twitchEmotes.any { it.code == e.code } }
        val emotes = (twitchEmotes + thirdPartyEmotes)

        val (adjustedMessage, adjustedEmotes) = adjustOverlayEmotes(appendedSpaceAdjustedMessage, emotes)
        val messageWithEmotes = when (message) {
            is PrivMessage       -> message.copy(message = adjustedMessage, originalMessage = appendedSpaceAdjustedMessage, emotes = adjustedEmotes)
            is WhisperMessage    -> message.copy(message = adjustedMessage, originalMessage = appendedSpaceAdjustedMessage, emotes = adjustedEmotes)
            is UserNoticeMessage -> message.copy(
                childMessage = message.childMessage?.copy(
                    message = adjustedMessage,
                    originalMessage = appendedSpaceAdjustedMessage,
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

    private fun getChannelBadgeUrl(channel: UserName?, set: String, version: String) = channelBadges[channel]?.get(set)?.versions?.get(version)?.imageUrlHigh

    private fun getGlobalBadgeUrl(set: String, version: String) = globalBadges[set]?.versions?.get(version)?.imageUrlHigh

    private fun getBadgeTitle(channel: UserName?, set: String, version: String): String? {
        return channelBadges[channel]?.get(set)?.versions?.get(version)?.title
            ?: globalBadges[set]?.versions?.get(version)?.title
    }

    private fun getFfzModBadgeUrl(channel: UserName?): String? = ffzModBadges[channel]

    private fun getFfzVipBadgeUrl(channel: UserName?): String? = ffzVipBadges[channel]

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

    suspend fun loadUserStateEmotes(globalEmoteSetIds: List<String>, followerEmoteSetIds: Map<UserName, List<String>>) = withContext(Dispatchers.Default) {
        twitchEmotes.clear()
        val combined = (globalEmoteSetIds + followerEmoteSetIds.values.flatten()).distinct()
        val sets = dankChatApiClient.getUserSets(combined)
            .getOrNull()
            .orEmpty()

        sets.forEach { emoteSet ->
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
            emoteSet.emotes.mapToGenericEmotes(type).forEach {
                twitchEmotes[it.code] = it
            }
        }
    }

    suspend fun setFFZEmotes(channel: UserName, ffzResult: FFZChannelDto) = withContext(Dispatchers.Default) {
        val emotes = ffzResult.sets
            .flatMap { set ->
                set.value.emotes.mapNotNull {
                    parseFFZEmote(it, channel)
                }
            }
            .associateBy { it.code }
        ffzEmotes[channel] = emotes
        ffzResult.room.modBadgeUrls?.let { ffzModBadges[channel] = "https:" + (it["4"] ?: it["2"] ?: it["1"]) }
        ffzResult.room.vipBadgeUrls?.let { ffzVipBadges[channel] = "https:" + (it["4"] ?: it["2"] ?: it["1"]) }
    }

    suspend fun setFFZGlobalEmotes(ffzResult: FFZGlobalDto) = withContext(Dispatchers.Default) {
        globalFFZEmotes.clear()
        val emotes = ffzResult.sets
            .filter { it.key in ffzResult.defaultSets }
            .flatMap { (_, emoteSet) ->
                emoteSet.emotes.mapNotNull { emote ->
                    parseFFZEmote(emote, channel = null)
                }
            }
            .associateBy { it.code }
        globalFFZEmotes.putAll(emotes)
    }

    suspend fun setBTTVEmotes(channel: UserName, bttvResult: BTTVChannelDto) = withContext(Dispatchers.Default) {
        val emotes = (bttvResult.emotes + bttvResult.sharedEmotes).associate {
            val emote = parseBTTVEmote(it)
            emote.code to emote
        }
        bttvEmotes[channel] = emotes
    }

    suspend fun setBTTVGlobalEmotes(globalEmotes: List<BTTVGlobalEmoteDto>) = withContext(Dispatchers.Default) {
        globalBttvEmotes.clear()
        globalEmotes.forEach {
            val emote = parseBTTVGlobalEmote(it)
            globalBttvEmotes[emote.code] = emote
        }
    }

    suspend fun setSevenTVEmotes(channel: UserName, sevenTvResult: List<SevenTVEmoteDto>) = withContext(Dispatchers.Default) {
        if (sevenTvResult.isEmpty()) return@withContext

        sevenTVEmotes[channel] = sevenTvResult
            .filterUnlistedIfEnabled()
            .mapNotNull { parseSevenTVEmote(it, EmoteType.ChannelSevenTVEmote) }
            .associateBy { it.code }
    }

    suspend fun setSevenTVGlobalEmotes(sevenTvResult: List<SevenTVEmoteDto>) = withContext(Dispatchers.Default) {
        if (sevenTvResult.isEmpty()) return@withContext

        globalSevenTVEmotes.clear()
        sevenTvResult
            .filterUnlistedIfEnabled()
            .forEach { emote ->
                val parsed = parseSevenTVEmote(emote, EmoteType.GlobalSevenTVEmote) ?: return@forEach
                globalSevenTVEmotes[emote.name] = parsed
            }
    }

    suspend fun getEmotes(channel: UserName): List<GenericEmote> = withContext(Dispatchers.Default) {
        buildList {
            twitchEmotes.values.filterNot {
                it.emoteType is EmoteType.ChannelTwitchFollowerEmote && it.emoteType.channel != channel
            }.let { addAll(it) }

            ffzEmotes[channel]?.let { addAll(it.values) }
            bttvEmotes[channel]?.let { addAll(it.values) }
            sevenTVEmotes[channel]?.let { addAll(it.values) }

            addAll(globalFFZEmotes.values)
            addAll(globalBttvEmotes.values)
            addAll(globalSevenTVEmotes.values)
        }.sortedBy { it.code }
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
        val adjustedEmotes = emotes.sortedBy { it.position.first }

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
                    // TODO
                    emote.position = previousEmote.position
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
                        nextEmote.position = first..last
                    }
                }
            }
        }

        return adjustedMessage to adjustedEmotes
    }

    private fun parseMessageForEmote(emote: GenericEmote, messageSplits: List<String>, existing: List<ChatMessageEmote> = listOf()): List<ChatMessageEmote> {
        var i = 0
        val parsed = mutableListOf<ChatMessageEmote>()
        messageSplits.forEach { split ->
            if (emote.code == split.trim() && !existing.any { it.code == emote.code }) {
                parsed += ChatMessageEmote(
                    position = i..i + split.length,
                    url = emote.url,
                    id = emote.id,
                    code = emote.code,
                    scale = emote.scale,
                    isOverlayEmote = emote.isOverlayEmote
                )
            }
            i += split.length + 1
        }

        return parsed
    }

    private fun parseTwitchEmotes(emoteTag: String, original: String, appendedSpaces: List<Int>, removedSpaces: List<Int>): List<ChatMessageEmote> {
        if (emoteTag.isEmpty()) {
            return emptyList()
        }

        // Characters with supplementary codepoints have two chars and need to be considered into emote positioning
        val supplementaryCodePointPositions = original.supplementaryCodePointPositions
        val emotes = mutableListOf<ChatMessageEmote>()
        for (emote in emoteTag.split('/')) {
            val split = emote.split(':')
            // bad emote data :)
            if (split.size != 2) continue

            val (id, positions) = split
            val pairs = positions.split(',')
            // bad emote data :)
            if (pairs.isEmpty()) continue

            // skip over invalid parsed data, adjust positions
            val parsedPositions = pairs.mapNotNull { pos ->
                val pair = pos.split('-')
                if (pair.size != 2) return@mapNotNull null

                val start = pair[0].toIntOrNull() ?: return@mapNotNull null
                val end = pair[1].toIntOrNull() ?: return@mapNotNull null

                val removedSpaceExtra = removedSpaces.count { it < start }
                val unicodeExtra = supplementaryCodePointPositions.count { it < start - removedSpaceExtra }
                val spaceExtra = appendedSpaces.count { it < start + unicodeExtra }
                val fixedStart = start + unicodeExtra + spaceExtra - removedSpaceExtra
                val fixedEnd = end + unicodeExtra + spaceExtra - removedSpaceExtra

                // be extra safe in case twitch sends invalid emote ranges :)
                fixedStart.coerceAtLeast(minimumValue = 0)..(fixedEnd + 1).coerceAtMost(original.length)
            }

            val code = original.substring(parsedPositions[0].first, parsedPositions[0].last)
            emotes.addAll(parsedPositions.map {
                ChatMessageEmote(
                    position = it,
                    url = TWITCH_EMOTE_TEMPLATE.format(id, TWITCH_EMOTE_SIZE),
                    id = id,
                    code = code,
                    scale = 1,
                    isTwitch = true
                )
            })
        }
        return emotes
    }

    private fun parseBTTVEmote(emote: BTTVEmoteDto): GenericEmote {
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
            emoteType = EmoteType.ChannelBTTVEmote,
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
        val (scale, url) = when {
            emote.urls["4"] != null -> 1 to emote.urls.getValue("4")
            emote.urls["2"] != null -> 2 to emote.urls.getValue("2")
            else                    -> 4 to emote.urls["1"]
        }
        url ?: return null
        val lowResUrl = emote.urls["2"] ?: emote.urls["1"] ?: return null
        val type = when (channel) {
            null -> EmoteType.GlobalFFZEmote
            else -> EmoteType.ChannelFFZEmote
        }
        return GenericEmote(name, "https:$url", "https:$lowResUrl", "$id", scale, type)
    }

    private fun parseSevenTVEmote(emote: SevenTVEmoteDto, type: EmoteType): GenericEmote? {
        val urls = emote.urls.associate { (size, url) -> size to url }
        return GenericEmote(
            code = emote.name,
            url = urls["4"] ?: return null,
            lowResUrl = urls["2"] ?: urls["1"] ?: return null,
            id = emote.id,
            scale = 1,
            emoteType = type,
            isOverlayEmote = SevenTVEmoteVisibility.ZERO_WIDTH in emote.visibility
        )
    }

    private fun List<SevenTVEmoteDto>.filterUnlistedIfEnabled(): List<SevenTVEmoteDto> = when {
        preferences.unlistedSevenTVEmotesEnabled -> this
        else                                     -> filterNot { SevenTVEmoteVisibility.UNLISTED in it.visibility }
    }

    companion object {
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