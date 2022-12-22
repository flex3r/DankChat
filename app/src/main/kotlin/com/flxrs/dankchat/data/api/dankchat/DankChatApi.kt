package com.flxrs.dankchat.data.api.dankchat

import io.ktor.client.*
import io.ktor.client.request.*

class DankChatApi(private val ktorClient: HttpClient) {

    suspend fun getSets(ids: String) = ktorClient.get("sets") {
        parameter("id", ids)
    }

    suspend fun getDankChatBadges() = ktorClient.get("badges")
}