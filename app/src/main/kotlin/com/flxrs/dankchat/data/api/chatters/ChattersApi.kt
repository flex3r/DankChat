package com.flxrs.dankchat.data.api.chatters

import com.flxrs.dankchat.data.UserName
import io.ktor.client.HttpClient
import io.ktor.client.request.get

class ChattersApi(private val ktorClient: HttpClient) {

    suspend fun getChatters(channel: UserName) = ktorClient.get("group/user/$channel/chatters")
}