package com.flxrs.dankchat.data.twitch.connection.dto.moderation

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class ModerationActionType {
    @SerialName("timeout")
    Timeout,

    @SerialName("untimeout")
    Untimeout,

    @SerialName("ban")
    Ban,

    @SerialName("unban")
    Unban,

    @SerialName("mod")
    Mod,

    @SerialName("unmod")
    Unmod,

    @SerialName("clear")
    Clear,

    @SerialName("delete")
    Delete,
}
