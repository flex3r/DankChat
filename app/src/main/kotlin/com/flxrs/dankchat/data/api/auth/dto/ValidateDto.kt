package com.flxrs.dankchat.data.api.auth.dto

import androidx.annotation.Keep
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Keep
@Serializable
data class ValidateDto(
    @SerialName(value = "client_id") val clientId: String,
    @SerialName(value = "login") val login: String,
    @SerialName(value = "scopes") val scopes: List<String>,
    @SerialName(value = "user_id") val userId: String
)

@Keep
@Serializable
data class ValidateErrorDto(
    val status: Int,
    val message: String
)
