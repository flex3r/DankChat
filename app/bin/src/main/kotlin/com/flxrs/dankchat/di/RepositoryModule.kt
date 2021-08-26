package com.flxrs.dankchat.di

import com.flxrs.dankchat.service.ChatRepository
import com.flxrs.dankchat.service.DataRepository
import com.flxrs.dankchat.service.api.ApiManager
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object RepositoryModule {

    @Singleton
    @Provides
    fun provideDataRepository(apiManager: ApiManager, emoteManager: EmoteManager): DataRepository = DataRepository(apiManager, emoteManager)

    @Singleton
    @Provides
    fun provideChatRepository(apiManager: ApiManager, emoteManager: EmoteManager): ChatRepository = ChatRepository(apiManager, emoteManager)
}