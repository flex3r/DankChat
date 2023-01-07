package com.flxrs.dankchat.data.api.recentmessages

import com.flxrs.dankchat.data.UserName
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class RecentMessagesApi(private val ktorClient: HttpClient) {

    suspend fun getRecentMessages(channel: UserName, limit: Int) = ktorClient.get("recent-messages/$channel") {
        parameter("limit", limit)
    }
}