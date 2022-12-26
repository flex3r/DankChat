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

sealed class HelixError {
    object MissingScopes : HelixError()
    object NotLoggedIn : HelixError()
    object Unknown : HelixError()
    object WhisperSelf : HelixError()
    object NoVerifiedPhone : HelixError()
    object RecipientBlockedUser : HelixError()
    object WhisperRateLimited : HelixError()
    object RateLimited : HelixError()
    object BroadcasterTokenRequired : HelixError()
    object UserNotAuthorized : HelixError()
    object TargetAlreadyModded : HelixError()
    object TargetIsVip : HelixError()
    object TargetNotModded : HelixError()
    object TargetNotBanned : HelixError()
    object TargetAlreadyBanned : HelixError()
    object TargetCannotBeBanned : HelixError()
    object ConflictingBanOperation : HelixError()
    object InvalidColor : HelixError()
    data class MarkerError(val message: String?) : HelixError()
    object Forwarded : HelixError()
}