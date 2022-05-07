package com.flxrs.dankchat.data.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class RecentMessagesApiService(private val ktorClient: HttpClient) {

    suspend fun getRecentMessages(channel: String): HttpResponse = ktorClient.get("recent-messages/$channel")
}