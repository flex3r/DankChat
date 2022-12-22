package com.flxrs.dankchat.data.api.auth.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ValidateDto(
    @SerialName(value = "client_id") val clientId: String,
    @SerialName(value = "login") val login: String,
    @SerialName(value = "scopes") val scopes: List<String>,
    @SerialName(value = "user_id") val userId: String
)

