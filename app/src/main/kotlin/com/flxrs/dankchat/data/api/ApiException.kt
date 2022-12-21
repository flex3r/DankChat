package com.flxrs.dankchat.data.api

import io.ktor.http.*

data class ApiException(val status: HttpStatusCode, override val message: String?, override val cause: Throwable? = null) : Throwable(message, cause)