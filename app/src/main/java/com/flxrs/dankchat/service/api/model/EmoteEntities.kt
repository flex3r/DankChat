package com.flxrs.dankchat.service.api.model

import com.squareup.moshi.Json

sealed class EmoteEntities {
	sealed class FFZ {
		data class Room(@field:Json(name = "_id") val id: Int,
						@field:Json(name = "css") val css: String?,
						@field:Json(name = "display_name") val displayName: String,
						@field:Json(name = "id") val name: String,
						@field:Json(name = "is_group") val isGroup: Boolean,
						@field:Json(name = "mod_urls") val modUrls: Map<String, String>,
						@field:Json(name = "moderator_badge") val modBadgeUrl: String,
						@field:Json(name = "set") val setId: Int,
						@field:Json(name = "twitch_id") val twitchId: Int,
						@field:Json(name = "user_badges") val userBadges: Map<String, Array<String>>)

		data class EmoteOwner(@field:Json(name = "_id") val id: Int,
							  @field:Json(name = "display_name") val displayName: String,
							  @field:Json(name = "name") val name: String)

		data class Emote(@field:Json(name = "css") val css: String?,
						 @field:Json(name = "height") val height: Int,
						 @field:Json(name = "hidden") val isHidden: Boolean,
						 @field:Json(name = "id") val id: Int,
						 @field:Json(name = "margins") val margins: String?,
						 @field:Json(name = "modifier") val modifier: Boolean,
						 @field:Json(name = "name") val name: String,
						 @field:Json(name = "offset") val offset: String?,
						 @field:Json(name = "owner") val owner: EmoteOwner,
						 @field:Json(name = "public") val isPublic: Boolean,
						 @field:Json(name = "urls") val urls: Map<String, String>,
						 @field:Json(name = "width") val width: Int)

		data class EmoteSet(@field:Json(name = "_type") val type: Int,
							@field:Json(name = "css") val css: String?,
							@field:Json(name = "description") val description: String?,
							@field:Json(name = "emoticons") val emotes: Array<Emote>) {
			override fun equals(other: Any?): Boolean {
				if (this === other) return true
				if (javaClass != other?.javaClass) return false

				other as EmoteSet
				if (type != other.type) return false
				if (css != other.css) return false
				if (description != other.description) return false
				if (!emotes.contentEquals(other.emotes)) return false

				return true
			}

			override fun hashCode(): Int {
				var result = type
				result = 31 * result + (css?.hashCode() ?: 0)
				result = 31 * result + (description?.hashCode() ?: 0)
				result = 31 * result + emotes.contentHashCode()
				return result
			}
		}

		data class Result(@field:Json(name = "room") val room: Room,
						  @field:Json(name = "sets") val sets: Map<String, EmoteSet>)

		data class GlobalResult(@field:Json(name = "default_sets") val defaultSets: Array<Int>,
								@field:Json(name = "sets") val sets: Map<String, EmoteSet>,
								@field:Json(name = "users") val users: Map<String, Array<String>>) {
			override fun equals(other: Any?): Boolean {
				if (this === other) return true
				if (javaClass != other?.javaClass) return false

				other as GlobalResult
				if (!defaultSets.contentEquals(other.defaultSets)) return false
				if (sets != other.sets) return false
				if (users != other.users) return false

				return true
			}

			override fun hashCode(): Int {
				var result = defaultSets.contentHashCode()
				result = 31 * result + sets.hashCode()
				result = 31 * result + users.hashCode()
				return result
			}
		}
	}

	sealed class BTTV {
		data class Emote(@field:Json(name = "id") val id: String,
						 @field:Json(name = "channel") val channel: String,
						 @field:Json(name = "code") val code: String,
						 @field:Json(name = "imageType") val imageType: String)

		data class GlobalEmote(@field:Json(name = "id") val id: String,
							   @field:Json(name = "code") val code: String,
							   @field:Json(name = "channel") val channel: String?,
							   @field:Json(name = "restrictions") val restrictions: Restriction,
							   @field:Json(name = "imageType") val imageType: String)

		data class Restriction(@field:Json(name = "channels") val channels: Array<String>,
							   @field:Json(name = "games") val games: Array<String>) {
			override fun equals(other: Any?): Boolean {
				if (this === other) return true
				if (javaClass != other?.javaClass) return false

				other as Restriction
				if (!channels.contentEquals(other.channels)) return false
				if (!games.contentEquals(other.games)) return false

				return true
			}

			override fun hashCode(): Int {
				var result = channels.contentHashCode()
				result = 31 * result + games.contentHashCode()
				return result
			}
		}

		data class Result(@field:Json(name = "status") val status: Int,
						  @field:Json(name = "urlTemplate") val urlTemplate: String,
						  @field:Json(name = "emotes") val emotes: Array<BTTV.Emote>) {
			override fun equals(other: Any?): Boolean {
				if (this === other) return true
				if (javaClass != other?.javaClass) return false

				other as Result
				if (status != other.status) return false
				if (urlTemplate != other.urlTemplate) return false
				if (!emotes.contentEquals(other.emotes)) return false

				return true
			}

			override fun hashCode(): Int {
				var result = status
				result = 31 * result + urlTemplate.hashCode()
				result = 31 * result + emotes.contentHashCode()
				return result
			}
		}

		data class GlobalResult(@field:Json(name = "status") val status: Int,
								@field:Json(name = "urlTemplate") val urlTemplate: String,
								@field:Json(name = "emotes") val emotes: Array<BTTV.GlobalEmote>) {
			override fun equals(other: Any?): Boolean {
				if (this === other) return true
				if (javaClass != other?.javaClass) return false

				other as GlobalResult
				if (status != other.status) return false
				if (urlTemplate != other.urlTemplate) return false
				if (!emotes.contentEquals(other.emotes)) return false

				return true
			}

			override fun hashCode(): Int {
				var result = status
				result = 31 * result + urlTemplate.hashCode()
				result = 31 * result + emotes.contentHashCode()
				return result
			}
		}
	}
}