package com.flxrs.dankchat.service.api.model

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class RecentMessages(@field:Json(name = "messages") val messages: List<String>)