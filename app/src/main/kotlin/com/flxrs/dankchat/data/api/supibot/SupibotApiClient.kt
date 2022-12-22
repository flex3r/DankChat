package com.flxrs.dankchat.data.api.supibot

import com.flxrs.dankchat.data.api.supibot.dto.SupibotChannelsDto
import com.flxrs.dankchat.data.api.supibot.dto.SupibotCommandsDto
import com.flxrs.dankchat.data.api.supibot.dto.SupibotUserAliasesDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupibotApiClient @Inject constructor(private val supibotApi: SupibotApi) {

    suspend fun getSupibotCommands(): Result<SupibotCommandsDto> = runCatching {
        supibotApi.getCommands()
            .throwApiErrorOnFailure()
            .body()
    }

    suspend fun getSupibotChannels(): Result<SupibotChannelsDto> = runCatching {
        supibotApi.getChannels()
            .throwApiErrorOnFailure()
            .body()
    }

    suspend fun getSupibotUserAliases(user: String): Result<SupibotUserAliasesDto> = runCatching {
        supibotApi.getUserAliases(user)
            .throwApiErrorOnFailure()
            .body()
    }
}