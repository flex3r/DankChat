package com.flxrs.dankchat.data.api.helix

import com.flxrs.dankchat.data.api.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url

data class HelixApiException(
    val error: HelixError,
    override val status: HttpStatusCode,
    override val url: Url?,
    override val message: String? = null,
    override val cause: Throwable? = null
) : ApiException(status, url, message, cause)

sealed interface HelixError {
    data object MissingScopes : HelixError
    data object NotLoggedIn : HelixError
    data object Unknown : HelixError
    data object WhisperSelf : HelixError
    data object NoVerifiedPhone : HelixError
    data object RecipientBlockedUser : HelixError
    data object WhisperRateLimited : HelixError
    data object RateLimited : HelixError
    data object BroadcasterTokenRequired : HelixError
    data object UserNotAuthorized : HelixError
    data object TargetAlreadyModded : HelixError
    data object TargetIsVip : HelixError
    data object TargetNotModded : HelixError
    data object TargetNotBanned : HelixError
    data object TargetAlreadyBanned : HelixError
    data object TargetCannotBeBanned : HelixError
    data object ConflictingBanOperation : HelixError
    data object InvalidColor : HelixError
    data class MarkerError(val message: String?) : HelixError
    data object CommercialRateLimited : HelixError
    data object CommercialNotStreaming : HelixError
    data object MissingLengthParameter : HelixError
    data object RaidSelf : HelixError
    data object NoRaidPending : HelixError
    data class NotInRange(val validRange: IntRange?) : HelixError
    data object Forwarded : HelixError
    data object ShoutoutSelf : HelixError
    data object ShoutoutTargetNotStreaming : HelixError
}
