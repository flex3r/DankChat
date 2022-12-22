package com.flxrs.dankchat.data.api.helix

import com.flxrs.dankchat.data.api.ApiException
import io.ktor.http.*

data class HelixApiException(
    val error: HelixError,
    override val status: HttpStatusCode,
    override val message: String? = null,
    override val cause: Throwable? = null
) : ApiException(status, message, cause)

enum class HelixError {
    BadRequest,
    Forbidden,
    MissingScopes,
    NotLoggedIn,
    Unauthorized,
    Unknown,
    WhisperSelf,
    NoVerifiedPhone,
    RecipientBlockedUser,
    WhisperRateLimited,
    RateLimited,
}