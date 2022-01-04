package com.flxrs.dankchat.di

import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.service.ChatRepository
import com.flxrs.dankchat.service.CommandRepository
import com.flxrs.dankchat.service.DataRepository
import com.flxrs.dankchat.service.api.ApiManager
import com.flxrs.dankchat.service.twitch.connection.WebSocketChatConnection
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object RepositoryModule {

    @Singleton
    @Provides
    fun provideDataRepository(
        apiManager: ApiManager,
        emoteManager: EmoteManager
    ): DataRepository = DataRepository(apiManager, emoteManager)

    @Singleton
    @Provides
    fun provideChatRepository(
        apiManager: ApiManager,
        emoteManager: EmoteManager,
        @ReadConnection readConnection: WebSocketChatConnection,
        @WriteConnection writeConnection: WebSocketChatConnection,
        @ApplicationScope scope: CoroutineScope,
    ): ChatRepository = ChatRepository(apiManager, emoteManager, readConnection, writeConnection, scope)

    @Singleton
    @Provides
    fun provideCommandRepository(
        chatRepository: ChatRepository,
        apiManager: ApiManager,
        preferenceStore: DankChatPreferenceStore
    ) = CommandRepository(chatRepository, apiManager, preferenceStore)
}