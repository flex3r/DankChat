package com.flxrs.dankchat.service.twitch.emote

import android.util.LruCache
import com.flxrs.dankchat.service.api.ApiManager
import com.flxrs.dankchat.service.api.dto.BadgeDtos
import com.flxrs.dankchat.service.api.dto.EmoteDtos
import com.flxrs.dankchat.utils.extensions.supplementaryCodePointPositions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.MultiCallback
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

class EmoteManager @Inject constructor(private val apiManager: ApiManager) {
    private val twitchEmotes = ConcurrentHashMap<String, GenericEmote>()

    private val ffzEmotes = ConcurrentHashMap<String, HashMap<String, GenericEmote>>()
    private val globalFFZEmotes = ConcurrentHashMap<String, GenericEmote>()

    private val bttvEmotes = ConcurrentHashMap<String, HashMap<String, GenericEmote>>()
    private val globalBttvEmotes = ConcurrentHashMap<String, GenericEmote>()

    private val ffzModBadges = ConcurrentHashMap<String, String>()
    private val channelBadges = ConcurrentHashMap<String, BadgeDtos.Result>()
    private val globalBadges = ConcurrentHashMap<String, BadgeDtos.BadgeSet>()
    private val dankChatBadges = CopyOnWriteArrayList<BadgeDtos.DankChatBadge>()

    val gifCache = LruCache<String, GifDrawable>(128)
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

    fun getFFzModBadgeUrl(channel: String) = ffzModBadges[channel]

    fun getDankChatBadgeUrl(userId: String?) = dankChatBadges.find { it.users.any { id -> id == userId } }?.let { it.type to it.url }

    fun setChannelBadges(channel: String, dto: BadgeDtos.Result) {
        channelBadges[channel] = dto
    }

    fun setGlobalBadges(dto: BadgeDtos.Result) {
        globalBadges.putAll(dto.sets)
    }

    fun setDankChatBadges(dto: List<BadgeDtos.DankChatBadge>) {
        dankChatBadges.addAll(dto)
    }

    suspend fun setTwitchEmotes(twitchResult: EmoteDtos.Twitch.Result) = withContext(Dispatchers.Default) {
        val setMapping = twitchResult.sets.keys
            .map {
                async { apiManager.getUserSet(it) ?: EmoteDtos.Twitch.EmoteSet(it, "", "", 1) }
            }.awaitAll()
            .associateBy({ it.id }, { it.channelName })

        twitchEmotes.clear()
        twitchResult.sets.forEach {
            val type = when (val set = it.key) {
                "0", "42" -> EmoteType.GlobalTwitchEmote // 42 == monkey emote set, move them to the global emote section
                else -> EmoteType.ChannelTwitchEmote(setMapping[set] ?: "Twitch")
            }
            it.value.forEach { (name: String, id: Int) ->
                val code = when (type) {
                    is EmoteType.GlobalTwitchEmote -> EMOTE_REPLACEMENTS[name] ?: name
                    else -> name
                }
                val emote = GenericEmote(
                    code = code,
                    url = "$BASE_URL/${id}/$EMOTE_SIZE",
                    lowResUrl = "$BASE_URL/${id}/$LOW_RES_EMOTE_SIZE",
                    isGif = false,
                    id = "$id",
                    scale = 1,
                    emoteType = type
                )
                twitchEmotes[emote.code] = emote
            }
        }
    }

    suspend fun setFFZEmotes(channel: String, ffzResult: EmoteDtos.FFZ.Result) = withContext(Dispatchers.Default) {
        val emotes = hashMapOf<String, GenericEmote>()
        ffzResult.sets.forEach {
            it.value.emotes.forEach { emote ->
                val parsedEmote = parseFFZEmote(emote, channel)
                emotes[parsedEmote.code] = parsedEmote
            }
        }
        ffzEmotes[channel] = emotes
        ffzResult.room.modBadgeUrls?.let { ffzModBadges[channel] = "https:" + (it["4"] ?: it["2"] ?: it["1"]) }
    }

    suspend fun setFFZGlobalEmotes(ffzResult: EmoteDtos.FFZ.GlobalResult) = withContext(Dispatchers.Default) {
        globalFFZEmotes.clear()
        ffzResult.sets.forEach {
            it.value.emotes.forEach { emote ->
                val parsedEmote = parseFFZEmote(emote)
                globalFFZEmotes[parsedEmote.code] = parsedEmote
            }
        }
    }

    suspend fun setBTTVEmotes(channel: String, bttvResult: EmoteDtos.BTTV.Result) = withContext(Dispatchers.Default) {
        val emotes = hashMapOf<String, GenericEmote>()
        (bttvResult.emotes + bttvResult.sharedEmotes).forEach {
            val emote = parseBTTVEmote(it)
            emotes[emote.code] = emote
        }
        bttvEmotes[channel] = emotes
    }

    suspend fun setBTTVGlobalEmotes(globalEmotes: List<EmoteDtos.BTTV.GlobalEmote>) = withContext(Dispatchers.Default) {
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

            if (emote.code in OVERLAY_EMOTES) {
                var foundEmote = false
                // first, iterate over previous emotes until a regular emote is found
                for (j in i - 1 downTo 0) {
                    val previousEmote = adjustedEmotes[j]
                    if (previousEmote.code in OVERLAY_EMOTES) {
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
                    isGif = emote.isGif
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
                    url = "$BASE_URL/$id/$EMOTE_SIZE",
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

    private fun parseBTTVEmote(emote: EmoteDtos.BTTV.Emote): GenericEmote {
        val name = emote.code
        val id = emote.id
        val type = emote.imageType == "gif"
        val url = "$BTTV_CDN_BASE_URL$id/3x"
        val lowResUrl = "$BTTV_CDN_BASE_URL$id/2x"
        return GenericEmote(name, url, lowResUrl, type, id, 1, EmoteType.ChannelBTTVEmote)
    }

    private fun parseBTTVGlobalEmote(emote: EmoteDtos.BTTV.GlobalEmote): GenericEmote {
        val name = emote.code
        val id = emote.id
        val type = emote.imageType == "gif"
        val url = "$BTTV_CDN_BASE_URL$id/3x"
        val lowResUrl = "$BTTV_CDN_BASE_URL$id/2x"
        return GenericEmote(name, url, lowResUrl, type, id, 1, EmoteType.GlobalBTTVEmote)
    }

    private fun parseFFZEmote(emote: EmoteDtos.FFZ.Emote, channel: String = ""): GenericEmote {
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

        private const val BASE_URL = "https://static-cdn.jtvnw.net/emoticons/v1/"
        private const val EMOTE_SIZE = "3.0"
        private const val LOW_RES_EMOTE_SIZE = "2.0"
        private const val BTTV_CDN_BASE_URL = "https://cdn.betterttv.net/emote/"

        private val WHITESPACE_REGEX = Regex("\\s")
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