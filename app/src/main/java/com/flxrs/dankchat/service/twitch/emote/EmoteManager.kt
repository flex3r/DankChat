package com.flxrs.dankchat.service.twitch.emote

import androidx.collection.LruCache
import com.flxrs.dankchat.service.api.model.BadgeEntities
import com.flxrs.dankchat.service.api.model.EmoteEntities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.MultiCallback
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object EmoteManager {
	private const val BASE_URL = "https://static-cdn.jtvnw.net/emoticons/v1/"
	private const val EMOTE_SIZE = "3.0"
	private val emotePattern = Pattern.compile("(\\d+):((?:\\d+-\\d+,?)+)")

	private val twitchEmotes = ConcurrentHashMap<String, GenericEmote>()

	private val ffzEmotes = ConcurrentHashMap<String, HashMap<String, GenericEmote>>()
	private val globalFFZEmotes = ConcurrentHashMap<String, GenericEmote>()

	private const val BTTV_CDN_BASE_URL = "https://cdn.betterttv.net/emote/"
	private val bttvEmotes = ConcurrentHashMap<String, HashMap<String, GenericEmote>>()
	private val globalBttvEmotes = ConcurrentHashMap<String, GenericEmote>()

	private val channelBadges = ConcurrentHashMap<String, BadgeEntities.Result>()
	private val globalBadges = ConcurrentHashMap<String, BadgeEntities.BadgeSet>()

	val gifCache = LruCache<String, GifDrawable>(4 * 1024 * 1024)
	val gifCallback = MultiCallback(true)

	fun parseTwitchEmotes(emoteTag: String, original: String): List<ChatEmote> {
		val unicodeFixPositions = mutableListOf<Int>()
		var offset = 0
		var index = 0
		while (offset < original.length) {
			val codepoint = original.codePointAt(offset)
			if (codepoint > 65535) {
				unicodeFixPositions.add(offset - index)
				index++
			}
			offset += Character.charCount(codepoint)
		}

		val emotes = arrayListOf<ChatEmote>()
		val matcher = emotePattern.matcher(emoteTag)
		while (matcher.find()) {
			val id = matcher.group(1)
			val positions = matcher.group(2).split(',').map { pos ->
				val start = pos.substringBefore('-').toInt()
				val end = pos.substringAfter('-').toInt()
				val unicodeExtra = unicodeFixPositions.count { it < start }
				return@map "${(start + unicodeExtra)}-${(end + unicodeExtra + 1)}"
			}

			emotes.add(ChatEmote(positions, "$BASE_URL/$id/$EMOTE_SIZE", id, "", 1, false, isTwitch = true))
		}
		return emotes
	}

	fun parse3rdPartyEmotes(message: String, channel: String): List<ChatEmote> {
		val availableFFz = ffzEmotes[channel] ?: hashMapOf()
		val availableBttv = bttvEmotes[channel] ?: hashMapOf()
		val total = availableFFz.plus(availableBttv).plus(globalBttvEmotes).plus(globalFFZEmotes)
		val splits = message.split(Regex("\\s"))
		val emotes = arrayListOf<ChatEmote>()
		total.forEach {
			var i = 0
			val positions = mutableListOf<String>()
			splits.forEach { split ->
				if (it.key == split.trim()) {
					positions.add("$i-${i + split.length}")
				}
				i += split.length + 1
			}
			emotes.add(ChatEmote(positions, it.value.url, it.value.id, it.value.keyword, it.value.scale, it.value.isGif))
		}
		return emotes
	}

	fun getSubBadgeUrl(channel: String, set: String, version: String) = channelBadges[channel]?.sets?.get(set)?.versions?.get(version)?.imageUrlHigh

	fun getGlobalBadgeUrl(set: String, version: String) = globalBadges[set]?.versions?.get(version)?.imageUrlHigh

	fun setChannelBadges(channel: String, entity: BadgeEntities.Result) {
		channelBadges[channel] = entity
	}

	fun setGlobalBadges(entity: BadgeEntities.Result) {
		globalBadges.putAll(entity.sets)
	}

	suspend fun setTwitchEmotes(twitchResult: EmoteEntities.Twitch.Result) = withContext(Dispatchers.Default) {
		twitchResult.sets.forEach {
			it.value.forEach { emoteResult ->
				val emote = GenericEmote(emoteResult.name, "$BASE_URL/${emoteResult.id}/$EMOTE_SIZE", false, "${emoteResult.id}", 1)
				twitchEmotes[emote.keyword] = emote
			}
		}
	}

	suspend fun setFFZEmotes(channel: String, ffzResult: EmoteEntities.FFZ.Result) = withContext(Dispatchers.Default) {
		val emotes = hashMapOf<String, GenericEmote>()
		ffzResult.sets.forEach {
			it.value.emotes.forEach { emote ->
				val parsedEmote = parseFFZEmote(emote)
				emotes[parsedEmote.keyword] = parsedEmote
			}
		}
		ffzEmotes[channel] = emotes
	}

	suspend fun setFFZGlobalEmotes(ffzResult: EmoteEntities.FFZ.GlobalResult) = withContext(Dispatchers.Default) {
		ffzResult.sets.forEach {
			it.value.emotes.forEach { emote ->
				val parsedEmote = parseFFZEmote(emote)
				globalFFZEmotes[parsedEmote.keyword] = parsedEmote
			}
		}
	}

	suspend fun setBTTVEmotes(channel: String, bttvResult: EmoteEntities.BTTV.Result) = withContext(Dispatchers.Default) {
		val emotes = hashMapOf<String, GenericEmote>()
		bttvResult.emotes.forEach {
			val emote = parseBTTVEmote(it)
			emotes[emote.keyword] = emote
		}
		bttvEmotes[channel] = emotes
	}

	suspend fun setBTTVGlobalEmotes(bttvResult: EmoteEntities.BTTV.GlobalResult) = withContext(Dispatchers.Default) {
		bttvResult.emotes.forEach {
			val emote = parseBTTVGlobalEmote(it)
			globalBttvEmotes[emote.keyword] = emote
		}
	}

	fun getEmotesForSuggestions(channel: String): List<GenericEmote> {
		val result = mutableListOf<GenericEmote>()
		result.addAll(twitchEmotes.values)
		result.addAll(globalFFZEmotes.values)
		result.addAll(globalBttvEmotes.values)
		ffzEmotes[channel]?.let { result.addAll(it.values) }
		bttvEmotes[channel]?.let { result.addAll(it.values) }
		result.sort()
		return result
	}

	private fun parseBTTVEmote(emote: EmoteEntities.BTTV.Emote): GenericEmote {
		val name = emote.code
		val id = emote.id
		val type = emote.imageType == "gif"
		val url = "$BTTV_CDN_BASE_URL$id/3x"
		return GenericEmote(name, url, type, id, 1)
	}

	private fun parseBTTVGlobalEmote(emote: EmoteEntities.BTTV.GlobalEmote): GenericEmote {
		val name = emote.code
		val id = emote.id
		val type = emote.imageType == "gif"
		val url = "$BTTV_CDN_BASE_URL$id/3x"
		return GenericEmote(name, url, type, id, 1)
	}

	private fun parseFFZEmote(emote: EmoteEntities.FFZ.Emote): GenericEmote {
		val name = emote.name
		val id = emote.id
		val (scale, url) = when {
			emote.urls.containsKey("4") -> 1 to emote.urls.getValue("4")
			emote.urls.containsKey("2") -> 2 to emote.urls.getValue("2")
			else                        -> 4 to emote.urls.getValue("1")
		}
		return GenericEmote(name, "https:$url", false, "$id", scale)
	}
}