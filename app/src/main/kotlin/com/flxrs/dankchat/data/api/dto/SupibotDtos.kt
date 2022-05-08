package com.flxrs.dankchat.data.api.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SupibotCommandsDto(@SerialName(value = "data") val data: List<SupibotCommandDto>)

@Keep
@Serializable
data class SupibotCommandDto(@SerialName(value = "name") val name: String, @SerialName(value = "aliases") val aliases: List<String>)

@Keep
@Serializable
data class SupibotChannelsDto(@SerialName(value = "data") val data: List<SupibotChannelDto>)

@Keep
@Serializable
data class SupibotChannelDto(@SerialName(value = "name") val name: String, @SerialName(value = "mode") val mode: String) {
    val isActive: Boolean
        get() = mode != "Last seen" && mode != "Read"
}

@Keep
@Serializable
data class SupibotUserAliasesDto(@SerialName(value = "data") val data: List<SupibotUserAliasDto>)

@Keep
@Serializable
data class SupibotUserAliasDto(@SerialName(value = "name") val name: String)