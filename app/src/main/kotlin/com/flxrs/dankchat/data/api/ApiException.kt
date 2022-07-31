package com.flxrs.dankchat.data.api

data class ApiException(val status: Int, override val message: String?, override val cause: Throwable? = null) : Throwable(message, cause)