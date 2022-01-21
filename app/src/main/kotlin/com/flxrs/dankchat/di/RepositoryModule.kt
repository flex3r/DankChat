package com.flxrs.dankchat.di

import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.data.*
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.data.database.EmoteUsageDao
import com.flxrs.dankchat.data.database.RecentUploadsDao
import com.flxrs.dankchat.data.twitch.connection.WebSocketChatConnection
import com.flxrs.dankchat.data.twitch.emote.EmoteManager
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
        emoteManager: EmoteManager,
        recentUploadsRepository: RecentUploadsRepository
    ): DataRepository = DataRepository(apiManager, emoteManager, recentUploadsRepository)

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

    @Singleton
    @Provides
    fun provideEmoteUsageRepository(
        emoteDao: EmoteUsageDao
    ) = EmoteUsageRepository(emoteDao)

    @Singleton
    @Provides
    fun provideRecentUploadsRepository(
        recentUploadsDao: RecentUploadsDao
    ) = RecentUploadsRepository(recentUploadsDao)
}