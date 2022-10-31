package com.flxrs.dankchat.preferences.multientry

import kotlinx.serialization.Serializable

@Serializable
data class MultiEntryDto(val entry: String, val isRegex: Boolean, val matchUser: Boolean)