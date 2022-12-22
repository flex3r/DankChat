package com.flxrs.dankchat.data.api.chatters

import io.ktor.client.*
import io.ktor.client.request.*

class ChattersApi(private val ktorClient: HttpClient) {

    suspend fun getChatters(channel: String) = ktorClient.get("group/user/$channel/chatters")
}