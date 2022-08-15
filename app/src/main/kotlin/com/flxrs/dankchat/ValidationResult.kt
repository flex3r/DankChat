package com.flxrs.dankchat

sealed class ValidationResult {
    data class User(val username: String) : ValidationResult()
    object TokenInvalid : ValidationResult()
    object Failure : ValidationResult()
}
