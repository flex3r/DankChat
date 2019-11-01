package com.flxrs.dankchat.service.twitch.emote

import androidx.collection.LruCache
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.api.model.BadgeEntities
import com.flxrs.dankchat.service.api.model.EmoteEntities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.MultiCallback
import java.util.concurrent.ConcurrentHashMap

object EmoteManager {
    private const val BASE_URL = "https://static-cdn.jtvnw.net/emoticons/v1/"
    private const val EMOTE_SIZE = "3.0"
    private const val LOW_RES_EMOTE_SIZE = "2.0"

    private val twitchEmotes = ConcurrentHashMap<String, GenericEmote>()

    private val ffzEmotes = ConcurrentHashMap<String, HashMap<String, GenericEmote>>()
    private val globalFFZEmotes = ConcurrentHashMap<String, GenericEmote>()

    private const val BTTV_CDN_BASE_URL = "https://cdn.betterttv.net/emote/"
    private val bttvEmotes = ConcurrentHashMap<String, HashMap<String, GenericEmote>>()
    private val globalBttvEmotes = ConcurrentHashMap<String, GenericEmote>()

    private val channelBadges = ConcurrentHashMap<String, BadgeEntities.Result>()
    private val globalBadges = ConcurrentHashMap<String, BadgeEntities.BadgeSet>()

    private val thirdPartyRegex = Regex("\\s")

    private val emoteReplacements = mapOf(
        "[oO](_|\\.)[oO]" to "O_o",
        "\\&lt\\;3" to ":3",
        "\\:-?(p|P)" to ":P",
        "\\:-?[z|Z|\\|]" to ":Z",
        "\\:-?\\)" to ":)",
        "\\;-?(p|P)" to ";P",
        "R-?\\)" to "R)",
        "\\&gt\\;\\(" to ">( ",
        "\\:-?(o|O)" to ":O",
        "\\:-?[\\\\/]" to ":/",
        "\\:-?\\(" to ":(",
        "\\:-?D" to ":D",
        "\\;-?\\)" to ";)",
        "B-?\\)" to "B)"
    )

    val gifCache = LruCache<String, GifDrawable>(4 * 1024 * 1024)
    val gifCallback = MultiCallback(true)

    fun parseTwitchEmotes(emoteTag: String, original: String): List<ChatEmote> {
        if (emoteTag.isEmpty()) {
            return emptyList()
        }

        val unicodeFixPositions = mutableListOf<Int>()
        var offset = 0
        var index = 0
        while (offset < original.length) {
            val codepoint = original.codePointAt(offset)
            if (Character.isSupplementaryCodePoint(codepoint)) {
                unicodeFixPositions.add(offset - index)
                index++
            }
            offset += Character.charCount(codepoint)
        }

        val emotes = arrayListOf<ChatEmote>()
        emoteTag.split('/').forEach { emote ->
            val (id, positions) = emote.split(':')
            val parsedPositons = positions.split(',').map { pos ->
                val start = pos.substringBefore('-').toInt()
                val end = pos.substringAfter('-').toInt()
                return@map start to end + 1
            }
            val fixedParsedPositions = parsedPositons.map { (start, end) ->
                val extra = unicodeFixPositions.count { it < start }
                return@map "${(start + extra)}-${(end + extra)}"
            }
            val code = original.substring(
                parsedPositons.first().first,
                parsedPositons.first().second
            )

            emotes += ChatEmote(
                fixedParsedPositions,
                "$BASE_URL/$id/$EMOTE_SIZE",
                id,
                code,
                1,
                false,
                isTwitch = true
            )
        }
        return emotes
    }

    fun parse3rdPartyEmotes(message: String, channel: String = ""): List<ChatEmote> {
        val availableFFz = ffzEmotes[channel] ?: hashMapOf()
        val availableBttv = bttvEmotes[channel] ?: hashMapOf()
        val total = availableFFz.plus(availableBttv).plus(globalBttvEmotes).plus(globalFFZEmotes)
        val splits = message.split(thirdPartyRegex)
        val emotes = arrayListOf<ChatEmote>()
        total.forEach { emote ->
            var i = 0
            val positions = mutableListOf<String>()
            splits.forEach { split ->
                if (emote.key == split.trim()) {
                    positions.add("$i-${i + split.length}")
                }
                i += split.length + 1
            }
            if (positions.size > 0) {
                emotes += ChatEmote(
                    positions,
                    emote.value.url,
                    emote.value.id,
                    emote.value.keyword,
                    emote.value.scale,
                    emote.value.isGif
                )
            }
        }
        return emotes
    }

    fun getSubBadgeUrl(channel: String, set: String, version: String) =
        channelBadges[channel]?.sets?.get(set)?.versions?.get(version)?.imageUrlHigh

    fun getGlobalBadgeUrl(set: String, version: String) =
        globalBadges[set]?.versions?.get(version)?.imageUrlHigh

    suspend fun setChannelBadges(channel: String, entity: BadgeEntities.Result) =
        withContext(Dispatchers.Default) {
            channelBadges[channel] = entity
        }

    suspend fun setGlobalBadges(entity: BadgeEntities.Result) = withContext(Dispatchers.Default) {
        globalBadges.putAll(entity.sets)
    }

    suspend fun setTwitchEmotes(twitchResult: EmoteEntities.Twitch.Result) =
        withContext(Dispatchers.Default) {
            twitchEmotes.clear()
            val setMapping = TwitchApi.getUserSets(twitchResult.sets.keys.toList())
                ?.associateBy({ it.id }, { it.channelName })

            twitchResult.sets.forEach {
                val set = it.key
                val type = if (set == "0") {
                    EmoteType.GlobalTwitchEmote
                } else {
                    val channel = setMapping?.get(set) ?: "Twitch"
                    EmoteType.ChannelTwitchEmote(channel)
                }

                it.value.forEach { emoteResult ->
                    val keyword = when (type) {
                        is EmoteType.GlobalTwitchEmote -> emoteReplacements.getOrElse(emoteResult.name) { emoteResult.name }
                        else                           -> emoteResult.name
                    }
                    val emote = GenericEmote(
                        keyword,
                        "$BASE_URL/${emoteResult.id}/$EMOTE_SIZE",
                        "$BASE_URL/${emoteResult.id}/$LOW_RES_EMOTE_SIZE",
                        false,
                        "${emoteResult.id}",
                        1,
                        type
                    )
                    twitchEmotes[emote.keyword] = emote
                }
            }
        }

    suspend fun setFFZEmotes(channel: String, ffzResult: EmoteEntities.FFZ.Result) =
        withContext(Dispatchers.Default) {
            val emotes = hashMapOf<String, GenericEmote>()
            ffzResult.sets.forEach {
                it.value.emotes.forEach { emote ->
                    val parsedEmote = parseFFZEmote(emote, channel)
                    emotes[parsedEmote.keyword] = parsedEmote
                }
            }
            ffzEmotes[channel] = emotes
        }

    suspend fun setFFZGlobalEmotes(ffzResult: EmoteEntities.FFZ.GlobalResult) =
        withContext(Dispatchers.Default) {
            ffzResult.sets.forEach {
                it.value.emotes.forEach { emote ->
                    val parsedEmote = parseFFZEmote(emote)
                    globalFFZEmotes[parsedEmote.keyword] = parsedEmote
                }
            }
        }

    suspend fun setBTTVEmotes(channel: String, bttvResult: EmoteEntities.BTTV.Result) =
        withContext(Dispatchers.Default) {
            val emotes = hashMapOf<String, GenericEmote>()
            bttvResult.emotes.plus(bttvResult.sharedEmotes).forEach {
                val emote = parseBTTVEmote(it)
                emotes[emote.keyword] = emote
            }
            bttvEmotes[channel] = emotes
        }

    suspend fun setBTTVGlobalEmotes(globalEmotes: List<EmoteEntities.BTTV.GlobalEmote>) =
        withContext(Dispatchers.Default) {
            globalEmotes.forEach {
                val emote = parseBTTVGlobalEmote(it)
                globalBttvEmotes[emote.keyword] = emote
            }
        }

    suspend fun getEmotes(channel: String): List<GenericEmote> = withContext(Dispatchers.Default) {
        val result = mutableListOf<GenericEmote>()
        result.addAll(twitchEmotes.values)
        result.addAll(globalFFZEmotes.values)
        result.addAll(globalBttvEmotes.values)
        ffzEmotes[channel]?.let { result.addAll(it.values) }
        bttvEmotes[channel]?.let { result.addAll(it.values) }
        return@withContext result.sortedBy { it.keyword }
    }

    private fun parseBTTVEmote(emote: EmoteEntities.BTTV.Emote): GenericEmote {
        val name = emote.code
        val id = emote.id
        val type = emote.imageType == "gif"
        val url = "$BTTV_CDN_BASE_URL$id/3x"
        val lowResUrl = "$BTTV_CDN_BASE_URL$id/2x"
        return GenericEmote(name, url, lowResUrl, type, id, 1, EmoteType.ChannelBTTVEmote)
    }

    private fun parseBTTVGlobalEmote(emote: EmoteEntities.BTTV.GlobalEmote): GenericEmote {
        val name = emote.code
        val id = emote.id
        val type = emote.imageType == "gif"
        val url = "$BTTV_CDN_BASE_URL$id/3x"
        val lowResUrl = "$BTTV_CDN_BASE_URL$id/2x"
        return GenericEmote(name, url, lowResUrl, type, id, 1, EmoteType.GlobalBTTVEmote)
    }

    private fun parseFFZEmote(emote: EmoteEntities.FFZ.Emote, channel: String = ""): GenericEmote {
        val name = emote.name
        val id = emote.id
        val (scale, url) = when {
            emote.urls.containsKey("4") -> 1 to emote.urls.getValue("4")
            emote.urls.containsKey("2") -> 2 to emote.urls.getValue("2")
            else                        -> 4 to emote.urls.getValue("1")
        }
        val lowResUrl = emote.urls["2"] ?: emote.urls.getValue("1")
        val type = if (channel.isBlank()) {
            EmoteType.GlobalFFZEmote
        } else {
            EmoteType.ChannelFFZEmote
        }
        return GenericEmote(name, "https:$url", "https:$lowResUrl", false, "$id", scale, type)
    }
}