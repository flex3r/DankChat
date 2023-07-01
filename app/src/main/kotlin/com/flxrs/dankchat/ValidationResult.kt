package com.flxrs.dankchat

import com.flxrs.dankchat.data.UserName

sealed interface ValidationResult {
    data class User(val username: UserName) : ValidationResult
    data class IncompleteScopes(val username: UserName) : ValidationResult
    object TokenInvalid : ValidationResult
    object Failure : ValidationResult
}
