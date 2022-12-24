package com.flxrs.dankchat.data.api.recentmessages

import com.flxrs.dankchat.data.UserName
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class RecentMessagesApi(private val ktorClient: HttpClient) {

    suspend fun getRecentMessages(channel: UserName, limit: Int) = ktorClient.get("recent-messages/$channel") {
        parameter("limit", limit)
    }
}