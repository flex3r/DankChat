package com.flxrs.dankchat.service.api.model

import com.squareup.moshi.Json

data class RecentMessages(@field:Json(name = "messages") val messages: List<String>)