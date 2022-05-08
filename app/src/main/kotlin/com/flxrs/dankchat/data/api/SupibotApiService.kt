package com.flxrs.dankchat.data.api

import io.ktor.client.*
import io.ktor.client.request.*

class SupibotApiService(private val ktorClient: HttpClient) {
    suspend fun getChannels(platformName: String = "twitch") = ktorClient.get("bot/channel/list") {
        parameter("platformName", platformName)
    }

    suspend fun getCommands() = ktorClient.get("bot/command/list/")

    suspend fun getUserAliases(user: String) = ktorClient.get("bot/user/$user/alias/list/")
}