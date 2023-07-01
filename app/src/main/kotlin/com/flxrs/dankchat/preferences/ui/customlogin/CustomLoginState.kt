package com.flxrs.dankchat.preferences.ui.customlogin

import com.flxrs.dankchat.data.api.auth.dto.ValidateDto

sealed interface CustomLoginState {
    object Default : CustomLoginState
    object Validated : CustomLoginState
    object Loading : CustomLoginState

    object TokenEmpty : CustomLoginState
    object TokenInvalid : CustomLoginState
    data class MissingScopes(val missingScopes: Set<String>, val validation: ValidateDto, val token: String) : CustomLoginState
    data class Failure(val error: String) : CustomLoginState
}
