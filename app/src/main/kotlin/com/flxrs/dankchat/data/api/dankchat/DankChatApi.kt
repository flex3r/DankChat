package com.flxrs.dankchat.data.api.dankchat

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class DankChatApi(private val ktorClient: HttpClient) {

    suspend fun getSets(ids: String) = ktorClient.get("sets") {
        parameter("id", ids)
    }

    suspend fun getDankChatBadges() = ktorClient.get("badges")
}