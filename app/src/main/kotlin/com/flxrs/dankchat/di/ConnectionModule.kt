package com.flxrs.dankchat.di

import com.flxrs.dankchat.data.api.eventapi.EventSubClient
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.twitch.chat.ChatConnection
import com.flxrs.dankchat.data.twitch.chat.ChatConnectionType
import com.flxrs.dankchat.utils.ForegroundServiceState
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

const val READ_CONNECTION = "ReadConnection"
const val WRITE_CONNECTION = "WriteConnection"

@Module
class ConnectionModule {
    @Single
    @Named(READ_CONNECTION)
    fun provideReadConnection(
        httpClient: HttpClient,
        dispatchersProvider: DispatchersProvider,
        authDataStore: AuthDataStore,
        foregroundServiceState: ForegroundServiceState,
        chatChannelProvider: ChatChannelProvider,
    ): ChatConnection = ChatConnection(ChatConnectionType.Read, httpClient, authDataStore, dispatchersProvider, foregroundServiceState.active, chatChannelProvider.activeChannel)

    @Single
    @Named(WRITE_CONNECTION)
    fun provideWriteConnection(
        httpClient: HttpClient,
        dispatchersProvider: DispatchersProvider,
        authDataStore: AuthDataStore,
        foregroundServiceState: ForegroundServiceState,
        chatChannelProvider: ChatChannelProvider,
    ): ChatConnection = ChatConnection(ChatConnectionType.Write, httpClient, authDataStore, dispatchersProvider, foregroundServiceState.active, chatChannelProvider.activeChannel)

    @Single
    fun provideEventSubClient(
        helixApiClient: HelixApiClient,
        json: Json,
        foregroundServiceState: ForegroundServiceState,
        httpClient: HttpClient,
        dispatchersProvider: DispatchersProvider,
    ): EventSubClient = EventSubClient(helixApiClient, json, foregroundServiceState, httpClient, dispatchersProvider)
}
