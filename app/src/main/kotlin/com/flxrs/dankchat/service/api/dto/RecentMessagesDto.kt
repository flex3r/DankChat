package com.flxrs.dankchat.service.api.dto

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class RecentMessagesDto(@field:Json(name = "messages") val messages: List<String>)