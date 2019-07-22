package com.flxrs.dankchat.service.api.model

import com.squareup.moshi.Json

data class RecentMessages(@field:Json(name = "messages") val messages: Array<String>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecentMessages
        if (!messages.contentEquals(other.messages)) return false

        return true
    }

    override fun hashCode(): Int {
        return messages.contentHashCode()
    }
}