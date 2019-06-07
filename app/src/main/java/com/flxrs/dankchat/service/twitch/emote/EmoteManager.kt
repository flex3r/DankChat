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

	private val ffzEmotes = ConcurrentHashMap<String, HashMap<String, GenericEmote>>()
	private val globalFFZEmotes = ConcurrentHashMap<String, GenericEmote>()

	private const val BTTV_CDN_BASE_URL = "https://cdn.betterttv.net/emote/"
	private val bttvEmotes = hashMapOf<String, HashMap<String, GenericEmote>>()
	private val globalBttvEmotes = ConcurrentHashMap<String, GenericEmote>()

	private val channelBadges = ConcurrentHashMap<String, BadgeEntities.Result>()
	private val globalBadges = ConcurrentHashMap<String, BadgeEntities.BadgeSet>()

	val gifCache = LruCache<String, GifDrawable>(4 * 1024 * 1024)
	val gifCallback = MultiCallback(true)

	fun parseTwitchEmotes(message: String): List<ChatEmote> {
		val emotes = arrayListOf<ChatEmote>()
		val matcher = emotePattern.matcher(message)
		while (matcher.find()) {
			val id = matcher.group(1)
			emotes.add(ChatEmote(matcher.group(2).split(','), "$BASE_URL/$id/$EMOTE_SIZE", id, "", 1, false))
		}
		return emotes
	}

	fun parse3rdPartyEmotes(message: String, channel: String): List<ChatEmote> {
		val availableFFz = ffzEmotes[channel] ?: hashMapOf()
		val availableBttv = bttvEmotes[channel] ?: hashMapOf()
		val total = availableFFz.plus(availableBttv).plus(globalBttvEmotes).plus(globalFFZEmotes)
		val splits = message.split(' ')
		val emotes = arrayListOf<ChatEmote>()
		total.forEach {
			var i = 0
			val positions = mutableListOf<String>()
			splits.forEach { split ->
				if (it.key == split.trim()) {
					positions.add("$i-${i + split.length - 1}")
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

	suspend fun getEmoteKeywords(channel: String): List<String> = withContext(Dispatchers.Default) {
		val result = mutableListOf<String>()
		result.addAll(globalFFZEmotes.keys)
		result.addAll(globalBttvEmotes.keys)
		ffzEmotes[channel]?.let { result.addAll(it.keys) }
		bttvEmotes[channel]?.let { result.addAll(it.keys) }
		result.sort()
		return@withContext result
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