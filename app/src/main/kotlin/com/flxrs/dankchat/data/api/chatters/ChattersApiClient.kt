package com.flxrs.dankchat.data.api.chatters

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.chatters.dto.ChatterCountDto
import com.flxrs.dankchat.data.api.chatters.dto.ChattersDto
import com.flxrs.dankchat.data.api.chatters.dto.ChattersResultDto
import com.flxrs.dankchat.data.api.recoverNotFoundWith
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.body
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChattersApiClient @Inject constructor(private val chattersApi: ChattersApi, private val json: Json) {

    suspend fun getChatters(channel: UserName): Result<ChattersDto> = runCatching {
        chattersApi.getChatters(channel)
            .throwApiErrorOnFailure(json)
            .body<ChattersResultDto>()
            .chatters
    }.recoverNotFoundWith(default = ChattersDto.EMPTY)

    suspend fun getChatterCount(channel: UserName): Result<Int> = runCatching {
        chattersApi.getChatters(channel)
            .throwApiErrorOnFailure(json)
            .body<ChatterCountDto>()
            .chatterCount
    }.recoverNotFoundWith(default = 0)
}
