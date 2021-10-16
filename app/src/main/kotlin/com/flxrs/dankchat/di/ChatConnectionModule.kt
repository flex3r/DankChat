package com.flxrs.dankchat.di

import com.flxrs.dankchat.service.twitch.connection.ChatConnectionType
import com.flxrs.dankchat.service.twitch.connection.WebSocketChatConnection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
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
        @ApiOkHttpClient client: OkHttpClient,
        @ApplicationScope scope: CoroutineScope,
    ): WebSocketChatConnection = WebSocketChatConnection(ChatConnectionType.Read, client, scope)

    @Singleton
    @WriteConnection
    @Provides
    fun provideWriteConnection(
        @ApiOkHttpClient client: OkHttpClient,
        @ApplicationScope scope: CoroutineScope,
    ): WebSocketChatConnection = WebSocketChatConnection(ChatConnectionType.Write, client, scope)
}