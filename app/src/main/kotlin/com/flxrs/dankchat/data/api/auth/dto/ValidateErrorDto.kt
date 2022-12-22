package com.flxrs.dankchat.data.api.auth.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ValidateErrorDto(
    val status: Int,
    val message: String
)