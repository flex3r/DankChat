package com.flxrs.dankchat.data.api.supibot

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.supibot.dto.SupibotChannelsDto
import com.flxrs.dankchat.data.api.supibot.dto.SupibotCommandsDto
import com.flxrs.dankchat.data.api.supibot.dto.SupibotUserAliasesDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.body
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class SupibotApiClient(private val supibotApi: SupibotApi, private val json: Json) {

    suspend fun getSupibotCommands(): Result<SupibotCommandsDto> = runCatching {
        supibotApi.getCommands()
            .throwApiErrorOnFailure(json)
            .body()
    }

    suspend fun getSupibotChannels(): Result<SupibotChannelsDto> = runCatching {
        supibotApi.getChannels()
            .throwApiErrorOnFailure(json)
            .body()
    }

    suspend fun getSupibotUserAliases(user: UserName): Result<SupibotUserAliasesDto> = runCatching {
        supibotApi.getUserAliases(user)
            .throwApiErrorOnFailure(json)
            .body()
    }
}
