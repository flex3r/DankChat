package com.flxrs.dankchat.data.api.helix

data class HelixApiException(val error: HelixError, override val message: String? = null, override val cause: Throwable? = null) : Throwable(message, cause)

enum class HelixError {
    BadRequest,
    Forbidden,
    MissingScopes,
    NotLoggedIn,
    Unauthorized,
    Unknown
}