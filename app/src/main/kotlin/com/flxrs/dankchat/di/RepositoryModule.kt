package com.flxrs.dankchat.di

import com.flxrs.dankchat.data.*
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.data.database.EmoteUsageDao
import com.flxrs.dankchat.data.database.RecentUploadsDao
import com.flxrs.dankchat.data.twitch.connection.ChatConnection
import com.flxrs.dankchat.data.twitch.connection.PubSubManager
import com.flxrs.dankchat.data.twitch.emote.EmoteManager
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
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
        recentUploadsRepository: RecentUploadsRepository,
        dankChatPreferenceStore: DankChatPreferenceStore,
    ): DataRepository = DataRepository(apiManager, emoteManager, recentUploadsRepository, dankChatPreferenceStore)

    @Singleton
    @Provides
    fun provideChatRepository(
        apiManager: ApiManager,
        emoteManager: EmoteManager,
        @ReadConnection readConnection: ChatConnection,
        @WriteConnection writeConnection: ChatConnection,
        pubSubManager: PubSubManager,
        @ApplicationScope scope: CoroutineScope,
        dankChatPreferenceStore: DankChatPreferenceStore,
    ): ChatRepository = ChatRepository(apiManager, emoteManager, readConnection, writeConnection, pubSubManager, dankChatPreferenceStore, scope)

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
        emoteDao: EmoteUsageDao,
        @ApplicationScope scope: CoroutineScope,
    ) = EmoteUsageRepository(emoteDao, scope)

    @Singleton
    @Provides
    fun provideRecentUploadsRepository(
        recentUploadsDao: RecentUploadsDao
    ) = RecentUploadsRepository(recentUploadsDao)
}