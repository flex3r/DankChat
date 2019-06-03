package com.flxrs.dankchat.service.twitch.emote

import androidx.collection.LruCache
import com.flxrs.dankchat.service.api.model.BadgeEntity
import com.flxrs.dankchat.service.twitch.badge.BadgeSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.MultiCallback
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

object EmoteManager {
	private const val BASE_URL = "https://static-cdn.jtvnw.net/emoticons/v1/"
	private const val EMOTE_SIZE = "3.0"
	private val emotePattern = Pattern.compile("(\\d+):((?:\\d+-\\d+,?)+)")

	private const val FFZ_BASE_URL = "https://api.frankerfacez.com/v1/room/"
	private val ffzEmotes = ConcurrentHashMap<String, HashMap<String, Emote>>()

	private const val BTTV_BASE_URL = "https://cdn.betterttv.net/emote/"
	private const val BTTV_CHANNEL_BASE_URL = "https://api.betterttv.net/2/channels/"
	private const val BTTV_GLOBAL_URL = "https://api.betterttv.net/2/emotes/"
	private val bttvEmotes = hashMapOf<String, HashMap<String, Emote>>()
	private val globalBttvEmotes = ConcurrentHashMap<String, Emote>()
	private val loadedGlobal = AtomicBoolean(false)

	private const val TWITCH_SUBBADGES_BASE_URL = "https://badges.twitch.tv/v1/badges/channels/"
	private const val TWITCH_SUBBADGES_SUFFIX = "/display"
	private val channelBadges = ConcurrentHashMap<String, BadgeEntity.BadgeSets>()

	private const val TWITCH_BADGES_URL = "https://badges.twitch.tv/v1/badges/global/display"
	private val globalBadges = ConcurrentHashMap<String, BadgeSet>()

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
		val availableGlobal = globalBttvEmotes
		val total = availableFFz.plus(availableBttv).plus(availableGlobal)
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

	fun getGlobalBadgeUrl(set: String, version: String) = globalBadges[set]?.versions?.get(version)

	fun setChannelBadges(channel: String, entity: BadgeEntity.BadgeSets) {
		channelBadges[channel] = entity
	}

	suspend fun loadGlobalBadges() = withContext(Dispatchers.IO) {
		if (loadedGlobal.compareAndSet(false, true)) {
			val response = URL(TWITCH_BADGES_URL).readText()
			withContext(Dispatchers.Default) {
				val json = JSONObject(response)
				val sets = json.getJSONObject("badge_sets")
				sets.keys().forEach { set ->
					val versionsJson = sets.getJSONObject(set).getJSONObject("versions")
					val versions = mutableMapOf<String, String>()
					versionsJson.keys().forEach { version ->
						val url = versionsJson.getJSONObject(version).getString("image_url_4x")
						versions[version] = url
					}
					val badgeSet = BadgeSet(set, versions)
					globalBadges[set] = badgeSet
				}
			}
		}
	}

	suspend fun loadFfzEmotes(channel: String) = withContext(Dispatchers.IO) {
		val emotes = hashMapOf<String, Emote>()
		val response = URL("$FFZ_BASE_URL$channel").readText()
		withContext(Dispatchers.Default) {
			val json = JSONObject(response)
			val set = json.getJSONObject("room").getString("set")
			val emotesJson = json.getJSONObject("sets").getJSONObject(set).getJSONArray("emoticons")
			for (i in 0 until emotesJson.length()) {
				val emoteJson = emotesJson.getJSONObject(i)
				val name = emoteJson.getString("name")
				val urls = emoteJson.getJSONObject("urls")
				var scale = 1
				val url = urls.optString("4").ifEmpty {
					scale = 2
					urls.optString("2").ifEmpty {
						scale = 4
						urls.optString("1")
					}
				}
				val id = emoteJson.getString("id")
				val emote = Emote(name, "https:$url", false, id, scale)
				emotes[name] = emote
			}
			ffzEmotes[channel] = emotes
		}
	}

	suspend fun loadBttvEmotes(channel: String) = withContext(Dispatchers.IO) {
		val response = URL("$BTTV_CHANNEL_BASE_URL$channel").readText()
		withContext(Dispatchers.Default) {
			val emotes = hashMapOf<String, Emote>()
			val json = JSONObject(response)
			val emotesJson = json.getJSONArray("emotes")
			for (i in 0 until emotesJson.length()) {
				val emoteJson = emotesJson.getJSONObject(i)
				val emote = parseBttvEmote(emoteJson)
				emotes[emote.keyword] = emote
			}
			bttvEmotes[channel] = emotes
		}
	}

	suspend fun loadGlobalBttvEmotes() = withContext(Dispatchers.IO) {
		val response = URL(BTTV_GLOBAL_URL).readText()
		withContext(Dispatchers.Default) {
			val json = JSONObject(response)
			val emotesJson = json.getJSONArray("emotes")
			for (i in 0 until emotesJson.length()) {
				val emoteJson = emotesJson.getJSONObject(i)
				val emote = parseBttvEmote(emoteJson)
				globalBttvEmotes[emote.keyword] = emote
			}
		}
	}

	private fun parseBttvEmote(json: JSONObject): Emote {
		val name = json.getString("code")
		val id = json.getString("id")
		val type = json.getString("imageType") == "gif"
		val url = "$BTTV_BASE_URL$id/3x"
		return Emote(name, url, type, id, 1)
	}
}