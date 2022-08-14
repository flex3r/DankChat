package com.flxrs.dankchat

sealed class OAuthResult {
    // This is named UserName instead of ValidUser because it's also used in case of fallback
    data class UserName(val username: String) : OAuthResult()
    object OAuthTokenInvalid : OAuthResult()
    object OAuthValidationFailure : OAuthResult()
}
