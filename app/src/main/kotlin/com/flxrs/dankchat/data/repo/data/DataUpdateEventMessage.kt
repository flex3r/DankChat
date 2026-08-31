package com.flxrs.dankchat.data.repo.data

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.seventv.eventapi.SevenTVEventMessage

sealed interface DataUpdateEventMessage {
    val channel: UserName

    data class EmoteSetUpdated(
        override val channel: UserName,
        val event: SevenTVEventMessage.EmoteSetUpdated,
    ) : DataUpdateEventMessage

    data class ActiveEmoteSetChanged(
        override val channel: UserName,
        val actorName: DisplayName,
        val emoteSetName: String,
    ) : DataUpdateEventMessage

    data class BTTVEmoteAdded(
        override val channel: UserName,
        val emoteName: String,
    ) : DataUpdateEventMessage

    data class BTTVEmoteRenamed(
        override val channel: UserName,
        val oldEmoteName: String,
        val emoteName: String,
    ) : DataUpdateEventMessage

    data class BTTVEmoteRemoved(
        override val channel: UserName,
        val emoteName: String,
    ) : DataUpdateEventMessage
}
