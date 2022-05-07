package com.flxrs.dankchat.data.api

import io.ktor.client.*
import io.ktor.client.request.*

class TmiApiService(private val ktorClient: HttpClient) {

    suspend fun getChatters(channel: String) = ktorClient.get("group/user/$channel/chatters")
}