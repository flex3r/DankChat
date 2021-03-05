package com.flxrs.dankchat.service.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

sealed class EmoteDtos {

    sealed class Twitch {
        @Keep
        data class Emote(
            @field:Json(name = "code") val name: String,
            @field:Json(name = "id") val id: Int
        )

        @Keep
        data class Result(@field:Json(name = "emoticon_sets") val sets: Map<String, List<Emote>>)

        @Keep
        data class EmoteSet(
            @field:Json(name = "set_id") val id: String,
            @field:Json(name = "channel_name") val channelName: String,
            @field:Json(name = "channel_id") val channelId: String,
            @field:Json(name = "tier") val tier: Int

        )
    }

    sealed class FFZ {
        @Keep
        data class Emote(
            @field:Json(name = "urls") val urls: Map<String, String?>,
            @field:Json(name = "name") val name: String,
            @field:Json(name = "id") val id: Int
        )

        @Keep
        data class EmoteSet(
            @field:Json(name = "emoticons") val emotes: List<Emote>
        )

        @Keep
        data class Room(
            @field:Json(name = "mod_urls") val modBadgeUrls: Map<String, String?>?
        )

        @Keep
        data class Result(
            @field:Json(name = "room") val room: Room,
            @field:Json(name = "sets") val sets: Map<String, EmoteSet>
        )

        @Keep
        data class GlobalResult(
            @field:Json(name = "sets") val sets: Map<String, EmoteSet>
        )
    }

    sealed class BTTV {
        @Keep
        data class Emote(
            @field:Json(name = "id") val id: String,
            @field:Json(name = "channel") val channel: String,
            @field:Json(name = "code") val code: String,
            @field:Json(name = "imageType") val imageType: String
        )

        @Keep
        data class GlobalEmote(
            @field:Json(name = "id") val id: String,
            @field:Json(name = "code") val code: String,
            @field:Json(name = "restrictions") val restrictions: Restriction,
            @field:Json(name = "imageType") val imageType: String
        )

        @Keep
        data class Restriction(
            @field:Json(name = "channels") val channels: List<String>,
            @field:Json(name = "games") val games: List<String>,
            @field:Json(name = "emoticonSet") val emoticonSet: String
        )

        @Keep
        data class Result(
            @field:Json(name = "id") val id: String,
            @field:Json(name = "bots") val bots: List<String>,
            @field:Json(name = "channelEmotes") val emotes: List<Emote>,
            @field:Json(name = "sharedEmotes") val sharedEmotes: List<Emote>
        )
    }
}