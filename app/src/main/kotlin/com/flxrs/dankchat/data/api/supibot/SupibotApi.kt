package com.flxrs.dankchat.data.api.supibot

import com.flxrs.dankchat.data.UserName
import io.ktor.client.*
import io.ktor.client.request.*

class SupibotApi(private val ktorClient: HttpClient) {

    suspend fun getChannels(platformName: String = "twitch") = ktorClient.get("bot/channel/list") {
        parameter("platformName", platformName)
    }

    suspend fun getCommands() = ktorClient.get("bot/command/list/")

    suspend fun getUserAliases(user: UserName) = ktorClient.get("bot/user/$user/alias/list/")
}