package com.flxrs.dankchat.data.api.helix

import com.flxrs.dankchat.data.api.ApiException
import io.ktor.http.*

data class HelixApiException(
    val error: HelixError,
    override val status: HttpStatusCode,
    override val url: Url?,
    override val message: String? = null,
    override val cause: Throwable? = null
) : ApiException(status, url, message, cause)

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
    BroadcasterTokenRequired,
    UserNotAuthorized,
    TargetAlreadyModded,
    TargetIsVip,
    TargetNotModded,
}