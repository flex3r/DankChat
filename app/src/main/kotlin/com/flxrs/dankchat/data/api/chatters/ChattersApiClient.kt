package com.flxrs.dankchat.data.api.chatters

import com.flxrs.dankchat.data.api.chatters.dto.ChatterCountDto
import com.flxrs.dankchat.data.api.chatters.dto.ChattersDto
import com.flxrs.dankchat.data.api.chatters.dto.ChattersResultDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChattersApiClient @Inject constructor(private val chattersApi: ChattersApi, private val json: Json) {
    var count = 0

    suspend fun getChatters(channel: String): Result<ChattersDto> = runCatching {
        chattersApi.getChatters(channel)
            .throwApiErrorOnFailure(json)
            .body<ChattersResultDto>()
            .chatters
    }

    suspend fun getChatterCount(channel: String): Result<Int> = runCatching {
        chattersApi.getChatters(channel)
            .throwApiErrorOnFailure(json)
            .body<ChatterCountDto>()
            .chatterCount
    }
}