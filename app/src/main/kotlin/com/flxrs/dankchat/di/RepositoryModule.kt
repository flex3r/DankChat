package com.flxrs.dankchat.di

import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.data.database.dao.EmoteUsageDao
import com.flxrs.dankchat.data.database.dao.RecentUploadsDao
import com.flxrs.dankchat.data.repo.*
import com.flxrs.dankchat.data.twitch.connection.ChatConnection
import com.flxrs.dankchat.data.twitch.connection.PubSubManager
import com.flxrs.dankchat.data.repo.EmoteRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

// TODO remove
@InstallIn(SingletonComponent::class)
@Module
object RepositoryModule {

    @Singleton
    @Provides
    fun provideDataRepository(
        apiManager: ApiManager,
        emoteRepository: EmoteRepository,
        recentUploadsRepository: RecentUploadsRepository,
        dankChatPreferenceStore: DankChatPreferenceStore,
    ): DataRepository = DataRepository(apiManager, emoteRepository, recentUploadsRepository, dankChatPreferenceStore)

    @Singleton
    @Provides
    fun provideChatRepository(
        apiManager: ApiManager,
        emoteRepository: EmoteRepository,
        @ReadConnection readConnection: ChatConnection,
        @WriteConnection writeConnection: ChatConnection,
        pubSubManager: PubSubManager,
        @ApplicationScope scope: CoroutineScope,
        dankChatPreferenceStore: DankChatPreferenceStore,
        highlightsRepository: HighlightsRepository,
        ignoresRepository: IgnoresRepository,
    ): ChatRepository = ChatRepository(
        apiManager = apiManager,
        emoteRepository = emoteRepository,
        readConnection = readConnection,
        writeConnection = writeConnection,
        pubSubManager = pubSubManager,
        dankChatPreferenceStore = dankChatPreferenceStore,
        highlightsRepository = highlightsRepository,
        ignoresRepository = ignoresRepository,
        scope = scope,
    )

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