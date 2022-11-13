package com.flxrs.dankchat.preferences.multientry

import kotlinx.serialization.Serializable

@Serializable
@Deprecated("Don't use, only kept for compatibility")
data class MultiEntryDto(val entry: String, val isRegex: Boolean, val matchUser: Boolean)