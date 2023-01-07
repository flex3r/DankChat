package com.flxrs.dankchat.data.twitch.connection.dto.whisper

import androidx.annotation.Keep
import com.flxrs.dankchat.data.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class WhisperData(
    @SerialName("sent_ts") val timestamp: Long,
    @SerialName("message_id") val messageId: String,
    @SerialName("body") val message: String,
    @SerialName("from_id") val userId: UserId,
    val tags: WhisperDataTags,
    val recipient: WhisperDataRecipient,
)
