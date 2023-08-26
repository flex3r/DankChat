package com.flxrs.dankchat.data.api.seventv.eventapi

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteDto

sealed interface SevenTVEventMessage {

    data class EmoteSetUpdated(
        val emoteSetId: String,
        val actorName: DisplayName,
        val added: List<SevenTVEmoteDto>,
        val removed: List<RemovedEmote>,
        val updated: List<UpdatedEmote>,
    ) : SevenTVEventMessage {
        data class UpdatedEmote(val id: String, val name: String, val oldName: String)
        data class RemovedEmote(val id: String, val name: String)
    }

    data class UserUpdated(val actorName: DisplayName, val connectionIndex: Int, val emoteSetId: String, val oldEmoteSetId: String) : SevenTVEventMessage
}
