package com.flxrs.dankchat.data.api.seventv.eventapi.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@SerialName("0")
data class DispatchMessage(override val d: DispatchData) : DataMessage

@Serializable
@JsonClassDiscriminator(discriminator = "type")
sealed interface DispatchData : Data {
    val body: ChangeMapData
}

interface ChangeMapData {
    val id: String
    val actor: Actor
}

@Serializable
data class Actor(@SerialName("display_name") val displayName: DisplayName)

@Serializable
@SerialName("emote_set.update")
data class EmoteSetDispatchData(
    override val body: EmoteSetChangeMapData
) : DispatchData

@Serializable
data class EmoteSetChangeMapData(
    override val id: String,
    override val actor: Actor,
    val pushed: List<EmoteChangeField>?,
    val pulled: List<EmoteChangeField>?,
    val updated: List<EmoteChangeField>?
) : ChangeMapData

@Serializable
@JsonClassDiscriminator("key")
sealed interface ChangeField

@Serializable
@SerialName("emotes")
data class EmoteChangeField(val value: SevenTVEmoteDto?, @SerialName("old_value") val oldValue: SevenTVEmoteDto?) : ChangeField

@Serializable
@SerialName("user.update")
data class UserDispatchData(
    override val body: UserChangeMapData
) : DispatchData

@Serializable
data class UserChangeMapData(
    override val id: String,
    override val actor: Actor,
    val updated: List<UserChangeFields>?
) : ChangeMapData

@Serializable
@SerialName("connections")
data class UserChangeFields(val value: List<UserChangeField>?, val index: Int) : ChangeField

@Serializable
sealed interface UserChangeField : ChangeField

@Serializable
@SerialName("emote_set")
data class EmoteSetChangeField(val value: EmoteSet, @SerialName("old_value") val oldValue: EmoteSet) : UserChangeField

@Keep
@Serializable
@SerialName("emote_set_id")
data object EmoteSetIdChangeField : UserChangeField

@Serializable
data class EmoteSet(val id: String)
