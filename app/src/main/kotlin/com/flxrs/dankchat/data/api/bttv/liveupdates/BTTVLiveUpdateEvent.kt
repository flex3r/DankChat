package com.flxrs.dankchat.data.api.bttv.liveupdates

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.api.bttv.dto.BTTVEmoteDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

sealed interface BTTVLiveUpdateEvent {
    val channelId: UserId

    data class EmoteAdded(
        override val channelId: UserId,
        val emote: BTTVEmoteDto,
    ) : BTTVLiveUpdateEvent

    data class EmoteUpdated(
        override val channelId: UserId,
        val emote: BTTVEmoteDto,
    ) : BTTVLiveUpdateEvent

    data class EmoteRemoved(
        override val channelId: UserId,
        val emoteId: String,
    ) : BTTVLiveUpdateEvent
}

@Serializable
private data class BTTVLiveUpdateMessage(
    val name: String,
    val data: JsonObject,
)

internal fun Json.decodeBTTVLiveUpdateEvent(message: String): BTTVLiveUpdateEvent? {
    val envelope = runCatching { decodeFromString<BTTVLiveUpdateMessage>(message) }.getOrNull() ?: return null
    val channelId = envelope.data["channel"]
        ?.jsonPrimitive
        ?.content
        ?.toBTTVChannelId() ?: return null

    return when (envelope.name) {
        "emote_create" -> {
            val emote = envelope.data["emote"]?.let { runCatching { decodeFromJsonElement<BTTVEmoteDto>(it) }.getOrNull() } ?: return null
            BTTVLiveUpdateEvent.EmoteAdded(channelId, emote)
        }

        "emote_update" -> {
            val emote = envelope.data["emote"]?.let { runCatching { decodeFromJsonElement<BTTVEmoteDto>(it) }.getOrNull() } ?: return null
            BTTVLiveUpdateEvent.EmoteUpdated(channelId, emote)
        }

        "emote_delete" -> {
            val emoteId = envelope.data["emoteId"]
                ?.jsonPrimitive
                ?.content
                ?.takeIf(String::isNotBlank) ?: return null
            BTTVLiveUpdateEvent.EmoteRemoved(channelId, emoteId)
        }

        else -> null
    }
}

private fun String.toBTTVChannelId(): UserId? = takeIf { it.startsWith(TWITCH_CHANNEL_PREFIX) }
    ?.removePrefix(TWITCH_CHANNEL_PREFIX)
    ?.takeIf(String::isNotBlank)
    ?.let(::UserId)

internal const val TWITCH_CHANNEL_PREFIX = "twitch:"
