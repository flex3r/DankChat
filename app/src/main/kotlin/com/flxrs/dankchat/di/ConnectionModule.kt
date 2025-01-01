package com.flxrs.dankchat.di

import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.twitch.chat.ChatConnection
import com.flxrs.dankchat.data.twitch.chat.ChatConnectionType
import com.flxrs.dankchat.data.twitch.pubsub.PubSubManager
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

data object ReadConnection
data object WriteConnection

@Module
class ConnectionModule {

    @Single
    @Named(type = ReadConnection::class)
    fun provideReadConnection(
        @Named(type = WebSocketOkHttpClient::class) client: OkHttpClient,
        dispatchersProvider: DispatchersProvider,
        preferenceStore: DankChatPreferenceStore,
    ): ChatConnection = ChatConnection(ChatConnectionType.Read, client, preferenceStore, dispatchersProvider)

    @Single
    @Named(type = WriteConnection::class)
    fun provideWriteConnection(
        @Named(type = WebSocketOkHttpClient::class) client: OkHttpClient,
        dispatchersProvider: DispatchersProvider,
        preferenceStore: DankChatPreferenceStore,
    ): ChatConnection = ChatConnection(ChatConnectionType.Write, client, preferenceStore, dispatchersProvider)

    @Single
    fun providePubSubManager(
        @Named(type = WebSocketOkHttpClient::class) client: OkHttpClient,
        dispatchersProvider: DispatchersProvider,
        preferenceStore: DankChatPreferenceStore,
        helixApiClient: HelixApiClient,
        json: Json,
    ): PubSubManager = PubSubManager(helixApiClient, preferenceStore, client, json, dispatchersProvider)
}
