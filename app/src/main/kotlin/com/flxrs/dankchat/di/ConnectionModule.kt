package com.flxrs.dankchat.di

import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.twitch.chat.ChatConnection
import com.flxrs.dankchat.data.twitch.chat.ChatConnectionType
import com.flxrs.dankchat.data.twitch.pubsub.PubSubManager
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ReadConnection

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class WriteConnection

@InstallIn(SingletonComponent::class)
@Module
object ChatConnectionModule {

    @Singleton
    @ReadConnection
    @Provides
    fun provideReadConnection(
        @WebSocketOkHttpClient client: OkHttpClient,
        @ApplicationScope scope: CoroutineScope,
        preferenceStore: DankChatPreferenceStore,
    ): ChatConnection = ChatConnection(ChatConnectionType.Read, client, scope, preferenceStore)

    @Singleton
    @WriteConnection
    @Provides
    fun provideWriteConnection(
        @WebSocketOkHttpClient client: OkHttpClient,
        @ApplicationScope scope: CoroutineScope,
        preferenceStore: DankChatPreferenceStore,
    ): ChatConnection = ChatConnection(ChatConnectionType.Write, client, scope, preferenceStore)

    @Singleton
    @Provides
    fun providePubSubManager(
        @WebSocketOkHttpClient client: OkHttpClient,
        @ApplicationScope scope: CoroutineScope,
        preferenceStore: DankChatPreferenceStore,
        helixApiClient: HelixApiClient,
        json: Json,
    ): PubSubManager = PubSubManager(helixApiClient, preferenceStore, client, scope, json)
}
