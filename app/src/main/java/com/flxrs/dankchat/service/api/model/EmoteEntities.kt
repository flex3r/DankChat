package com.flxrs.dankchat.service.api.model

import com.squareup.moshi.Json

sealed class EmoteEntities {

    sealed class Twitch {
        data class Emote(
            @field:Json(name = "code") val name: String,
            @field:Json(name = "id") val id: Int
        )

        data class Result(@field:Json(name = "emoticon_sets") val sets: Map<String, List<Emote>>)

        data class EmoteSet(
            @field:Json(name = "set_id") val id: String,
            @field:Json(name = "channel_name") val channelName: String,
            @field:Json(name = "channel_id") val channelId: String,
            @field:Json(name = "tier") val tier: Int

        )
    }

    sealed class FFZ {
        data class Room(
            @field:Json(name = "_id") val id: Int,
            @field:Json(name = "css") val css: String?,
            @field:Json(name = "display_name") val displayName: String,
            @field:Json(name = "id") val name: String,
            @field:Json(name = "is_group") val isGroup: Boolean,
            @field:Json(name = "mod_urls") val modUrls: Map<String, String>,
            @field:Json(name = "moderator_badge") val modBadgeUrl: String,
            @field:Json(name = "set") val setId: Int,
            @field:Json(name = "twitch_id") val twitchId: Int,
            @field:Json(name = "user_badges") val userBadges: Map<String, List<String>>
        )

        data class EmoteOwner(
            @field:Json(name = "_id") val id: Int,
            @field:Json(name = "display_name") val displayName: String,
            @field:Json(name = "name") val name: String
        )

        data class Emote(
//            @field:Json(name = "css") val css: String?,
//            @field:Json(name = "height") val height: Int,
//            @field:Json(name = "hidden") val isHidden: Boolean,
//            @field:Json(name = "margins") val margins: String?,
//            @field:Json(name = "modifier") val modifier: Boolean,
//            @field:Json(name = "offset") val offset: String?,
//            @field:Json(name = "owner") val owner: EmoteOwner,
//            @field:Json(name = "public") val isPublic: Boolean,
//            @field:Json(name = "width") val width: Int,
            @field:Json(name = "urls") val urls: Map<String, String>,
            @field:Json(name = "name") val name: String,
            @field:Json(name = "id") val id: Int
        )

        data class EmoteSet(
//            @field:Json(name = "_type") val type: Int,
//            @field:Json(name = "css") val css: String?,
//            @field:Json(name = "description") val description: String?,
            @field:Json(name = "emoticons") val emotes: List<Emote>
        )

        data class Result(
            //@field:Json(name = "room") val room: Room,
            @field:Json(name = "sets") val sets: Map<String, EmoteSet>
        )

        data class GlobalResult(
            //@field:Json(name = "default_sets") val defaultSets: List<Int>,
            //@field:Json(name = "users") val users: Map<String, List<String>>,
            @field:Json(name = "sets") val sets: Map<String, EmoteSet>
        )
    }

    sealed class BTTV {
        data class Emote(
            @field:Json(name = "id") val id: String,
            @field:Json(name = "channel") val channel: String,
            @field:Json(name = "code") val code: String,
            @field:Json(name = "imageType") val imageType: String
        )

        data class GlobalEmote(
            @field:Json(name = "id") val id: String,
            @field:Json(name = "code") val code: String,
            @field:Json(name = "restrictions") val restrictions: Restriction,
            @field:Json(name = "imageType") val imageType: String
        )

        data class Restriction(
            @field:Json(name = "channels") val channels: List<String>,
            @field:Json(name = "games") val games: List<String>,
            @field:Json(name = "emoticonSet") val emoticonSet: String
        )

        data class Result(
            @field:Json(name = "id") val id: String,
            @field:Json(name = "bots") val bots: List<String>,
            @field:Json(name = "channelEmotes") val emotes: List<Emote>,
            @field:Json(name = "sharedEmotes") val sharedEmotes: List<Emote>
        )
    }
}