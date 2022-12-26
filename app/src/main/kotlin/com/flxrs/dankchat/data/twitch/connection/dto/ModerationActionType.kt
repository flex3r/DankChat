package com.flxrs.dankchat.data.twitch.connection.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ModerationActionType {
    @SerialName("timeout") Timeout,
    @SerialName("untimeout") Untimeout,
    @SerialName("ban") Ban,
    @SerialName("unban") Unban,
    @SerialName("mod") Mod,
    @SerialName("unmod") Unmod,
    @SerialName("clear") Clear,
}