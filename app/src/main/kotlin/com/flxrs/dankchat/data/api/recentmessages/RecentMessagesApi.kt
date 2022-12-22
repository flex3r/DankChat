package com.flxrs.dankchat.data.api.recentmessages

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class RecentMessagesApi(private val ktorClient: HttpClient) {

    suspend fun getRecentMessages(channel: String) = ktorClient.get("recent-messages/$channel")
}