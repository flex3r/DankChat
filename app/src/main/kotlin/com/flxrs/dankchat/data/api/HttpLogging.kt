package com.flxrs.dankchat.data.api

import io.ktor.util.AttributeKey

// Requests that carry this attribute are left out of the HTTP log
val SKIP_HTTP_LOGGING = AttributeKey<Unit>("SkipHttpLogging")
