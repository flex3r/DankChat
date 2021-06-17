package com.flxrs.dankchat.service.twitch.emote

import android.graphics.drawable.Drawable
import android.util.LruCache
import com.flxrs.dankchat.service.api.ApiManager
import com.flxrs.dankchat.service.api.dto.*
import com.flxrs.dankchat.utils.extensions.supplementaryCodePointPositions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.MultiCallback
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

class EmoteManager @Inject constructor(private val apiManager: ApiManager) {
    private val twitchEmotes = ConcurrentHashMap<String, GenericEmote>()
    private var krakenEmoteSets: List<String> = emptyList()

    private val ffzEmotes = ConcurrentHashMap<String, HashMap<String, GenericEmote>>()
    private val globalFFZEmotes = ConcurrentHashMap<String, GenericEmote>()

    private val bttvEmotes = ConcurrentHashMap<String, HashMap<String, GenericEmote>>()
    private val globalBttvEmotes = ConcurrentHashMap<String, GenericEmote>()

    private val ffzModBadges = ConcurrentHashMap<String, String>()
    private val ffzVipBadges = ConcurrentHashMap<String, String>()
    private val channelBadges = ConcurrentHashMap<String, TwitchBadgesDto>()
    private val globalBadges = ConcurrentHashMap<String, TwitchBadgeSetDto>()
    private val dankChatBadges = CopyOnWriteArrayList<DankChatBadgeDto>()

    val gifCache = LruCache<String, Drawable>(64)
    val gifCallback = MultiCallback(true)

    fun parse3rdPartyEmotes(message: String, channel: String = "", withTwitch: Boolean = false): List<ChatMessageEmote> {
        val splits = message.split(WHITESPACE_REGEX)
        val emotes = mutableListOf<ChatMessageEmote>()

        if (withTwitch) twitchEmotes.forEach { emotes.addAll(parseMessageForEmote(it.value, splits)) }
        ffzEmotes[channel]?.forEach { emotes.addAll(parseMessageForEmote(it.value, splits, emotes)) }
        bttvEmotes[channel]?.forEach { emotes.addAll(parseMessageForEmote(it.value, splits, emotes)) }
        globalFFZEmotes.forEach { emotes.addAll(parseMessageForEmote(it.value, splits, emotes)) }
        globalBttvEmotes.forEach { emotes.addAll(parseMessageForEmote(it.value, splits, emotes)) }

        return emotes
    }

    fun parseEmotes(message: String, channel: String, emoteTag: String, extraSpacePositions: List<Int>, removedSpacePositions: List<Int>): Pair<String, List<ChatMessageEmote>> {
        val twitchEmotes = parseTwitchEmotes(emoteTag, message, extraSpacePositions, removedSpacePositions)
        val thirdPartyEmotes = parse3rdPartyEmotes(message, channel).filterNot { e -> twitchEmotes.any { it.code == e.code } }
        val emotes = (twitchEmotes + thirdPartyEmotes)

        return adjustOverlayEmotes(message, emotes)
    }

    fun getChannelBadgeUrl(channel: String, set: String, version: String) = channelBadges[channel]?.sets?.get(set)?.versions?.get(version)?.imageUrlHigh

    fun getGlobalBadgeUrl(set: String, version: String) = globalBadges[set]?.versions?.get(version)?.imageUrlHigh

    fun getFfzModBadgeUrl(channel: String): String? = ffzModBadges[channel]

    fun getFfzVipBadgeUrl(channel: String): String? = ffzVipBadges[channel]

    fun getDankChatBadgeUrl(userId: String?) = dankChatBadges.find { it.users.any { id -> id == userId } }?.let { it.type to it.url }

    fun setChannelBadges(channel: String, dto: TwitchBadgesDto) {
        channelBadges[channel] = dto
    }

    fun setGlobalBadges(dto: TwitchBadgesDto) {
        globalBadges.putAll(dto.sets)
    }

    fun setDankChatBadges(dto: List<DankChatBadgeDto>) {
        dankChatBadges.addAll(dto)
    }

    suspend fun setTwitchEmotes(twitchResult: TwitchEmotesDto) = withContext(Dispatchers.Default) {
        krakenEmoteSets = twitchResult.sets.keys.toList()

        val setMapping = twitchResult.sets.keys
            .map {
                async {
                    runCatching { apiManager.getUserSet(it) }.getOrNull() ?: TwitchEmoteSetDto(it, "", "", 1, null)
                }
            }.awaitAll()
            .associateBy({ it.id }, { it.channelName })

        val existingBitEmotes = twitchEmotes.filterValues { it.emoteType is EmoteType.ChannelTwitchBitEmote }
        twitchEmotes.clear()
        twitchEmotes.putAll(existingBitEmotes)

        twitchResult.sets.forEach { (set, emotes) ->
            val type = when (set) {
                "0", "42" -> EmoteType.GlobalTwitchEmote // 42 == monkey emote set, move them to the global emote section
                else -> EmoteType.ChannelTwitchEmote(setMapping[set] ?: "Twitch")
            }

            emotes.mapToGenericEmotes(type).forEach {
                twitchEmotes[it.code] = it
            }
        }
    }

    suspend fun filterAndSetBitEmotes(emoteSets: List<String>) = withContext(Dispatchers.Default) {
        val filtered = emoteSets.filter { it !in krakenEmoteSets }
        val emoteSetsResult = filtered.map {
            async {
                runCatching { apiManager.getUserSet(it) }.getOrNull() ?: TwitchEmoteSetDto(it, "", "", 1, null)
            }
        }.awaitAll()

        emoteSetsResult.forEach { emoteSet ->
            val type = EmoteType.ChannelTwitchBitEmote(emoteSet.channelName)
            emoteSet.emotes.mapToGenericEmotes(type).forEach {
                twitchEmotes[it.code] = it
            }
        }
    }

    private fun List<TwitchEmoteDto>?.mapToGenericEmotes(type: EmoteType): List<GenericEmote> = this?.map { (name, id) ->
        val code = when (type) {
            is EmoteType.GlobalTwitchEmote -> EMOTE_REPLACEMENTS[name] ?: name
            else -> name
        }
        GenericEmote(
            code = code,
            url = TWITCH_EMOTE_TEMPLATE.format(id, TWITCH_EMOTE_SIZE),
            lowResUrl = TWITCH_EMOTE_TEMPLATE.format(id, TWITCH_LOW_RES_EMOTE_SIZE),
            isGif = false,
            id = id,
            scale = 1,
            emoteType = type
        )
    } ?: emptyList()

    suspend fun setFFZEmotes(channel: String, ffzResult: FFZChannelDto) = withContext(Dispatchers.Default) {
        val emotes = hashMapOf<String, GenericEmote>()
        ffzResult.sets.forEach {
            it.value.emotes.forEach { emote ->
                val parsedEmote = parseFFZEmote(emote, channel)
                emotes[parsedEmote.code] = parsedEmote
            }
        }
        ffzEmotes[channel] = emotes
        ffzResult.room.modBadgeUrls?.let { ffzModBadges[channel] = "https:" + (it["4"] ?: it["2"] ?: it["1"]) }
        ffzResult.room.vipBadgeUrls?.let { ffzVipBadges[channel] = "https:" + (it["4"] ?: it["2"] ?: it["1"]) }
    }

    suspend fun setFFZGlobalEmotes(ffzResult: FFZGlobalDto) = withContext(Dispatchers.Default) {
        globalFFZEmotes.clear()
        ffzResult.sets.forEach {
            it.value.emotes.forEach { emote ->
                val parsedEmote = parseFFZEmote(emote)
                globalFFZEmotes[parsedEmote.code] = parsedEmote
            }
        }
    }

    suspend fun setBTTVEmotes(channel: String, bttvResult: BTTVChannelDto) = withContext(Dispatchers.Default) {
        val emotes = hashMapOf<String, GenericEmote>()
        (bttvResult.emotes + bttvResult.sharedEmotes).forEach {
            val emote = parseBTTVEmote(it)
            emotes[emote.code] = emote
        }
        bttvEmotes[channel] = emotes
    }

    suspend fun setBTTVGlobalEmotes(globalEmotes: List<BTTVGlobalEmotesDto>) = withContext(Dispatchers.Default) {
        globalBttvEmotes.clear()
        globalEmotes.forEach {
            val emote = parseBTTVGlobalEmote(it)
            globalBttvEmotes[emote.code] = emote
        }
    }

    suspend fun getEmotes(channel: String): List<GenericEmote> = withContext(Dispatchers.Default) {
        val result = mutableListOf<GenericEmote>()
        result.addAll(twitchEmotes.values)
        ffzEmotes[channel]?.let { result.addAll(it.values) }
        bttvEmotes[channel]?.let { result.addAll(it.values) }
        result.addAll(globalFFZEmotes.values)
        result.addAll(globalBttvEmotes.values)
        return@withContext result.sortedBy { it.code }
    }

    private fun adjustOverlayEmotes(message: String, emotes: List<ChatMessageEmote>): Pair<String, List<ChatMessageEmote>> {
        var adjustedMessage = message
        val adjustedEmotes = emotes.sortedBy { it.position.first }

        for (i in adjustedEmotes.lastIndex downTo 0) {
            val emote = adjustedEmotes[i]

            if (emote.isOverlayEmote) {
                var foundEmote = false
                // first, iterate over previous emotes until a regular emote is found
                for (j in i - 1 downTo 0) {
                    val previousEmote = adjustedEmotes[j]
                    if (previousEmote.isOverlayEmote) {
                        continue
                    }

                    adjustedMessage = when (emote.position.last) {
                        adjustedMessage.length -> adjustedMessage.substring(0, emote.position.first)
                        else -> adjustedMessage.removeRange(emote.position)
                    }
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
                    isGif = emote.isGif,
                    isOverlayEmote = emote.code in OVERLAY_EMOTES
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
                    isGif = false,
                    isTwitch = true
                )
            })
        }
        return emotes
    }

    private fun parseBTTVEmote(emote: BTTVEmoteDto): GenericEmote {
        val name = emote.code
        val id = emote.id
        val type = emote.imageType == "gif"
        val url = BTTV_EMOTE_TEMPLATE.format(id, BTTV_EMOTE_SIZE)
        val lowResUrl = BTTV_EMOTE_TEMPLATE.format(id, BTTV_LOW_RES_EMOTE_SIZE)
        return GenericEmote(name, url, lowResUrl, type, id, 1, EmoteType.ChannelBTTVEmote)
    }

    private fun parseBTTVGlobalEmote(emote: BTTVGlobalEmotesDto): GenericEmote {
        val name = emote.code
        val id = emote.id
        val type = emote.imageType == "gif"
        val url = BTTV_EMOTE_TEMPLATE.format(id, BTTV_EMOTE_SIZE)
        val lowResUrl = BTTV_EMOTE_TEMPLATE.format(id, BTTV_LOW_RES_EMOTE_SIZE)
        return GenericEmote(name, url, lowResUrl, type, id, 1, EmoteType.GlobalBTTVEmote)
    }

    private fun parseFFZEmote(emote: FFZEmoteDto, channel: String = ""): GenericEmote {
        val name = emote.name
        val id = emote.id
        val (scale, url) = when {
            emote.urls.containsKey("4") && emote.urls["4"] != null -> 1 to emote.urls.getValue("4")
            emote.urls.containsKey("2") && emote.urls["2"] != null -> 2 to emote.urls.getValue("2")
            else -> 4 to emote.urls["1"]
        }
        val lowResUrl = emote.urls["2"] ?: emote.urls["1"]
        val type = when {
            channel.isBlank() -> EmoteType.GlobalFFZEmote
            else -> EmoteType.ChannelFFZEmote
        }
        return GenericEmote(name, "https:$url", "https:$lowResUrl", false, "$id", scale, type)
    }

    companion object {
        private val TAG = EmoteManager::class.java.simpleName

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