package com.flxrs.dankchat.data.twitch.connection

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WhisperData(
    @SerialName("sent_ts") val timestamp: Long,
    @SerialName("message_id") val messageId: String,
    @SerialName("body") val message: String,
    @SerialName("from_id") val userId: UserId,
    val tags: WhisperDataTags,
    val recipient: WhisperDataRecipient,
)

@Serializable
data class WhisperDataTags(
    @SerialName("login") val name: UserName,
    @SerialName("display_name") val displayName: DisplayName,
    val color: String,
    val emotes: List<WhisperDataEmote>,
    val badges: List<WhisperDataBadge>,
)

@Serializable
data class WhisperDataEmote(
    @SerialName("emote_id") val id: String,
    val start: Int,
    val end: Int,
)

@Serializable
data class WhisperDataBadge(
    val id: String,
    val version: String,
)

@Serializable
data class WhisperDataRecipient(
    val id: UserId,
    val color: String,
    @SerialName("username") val name: UserName,
    @SerialName("display_name") val displayName: DisplayName,
)