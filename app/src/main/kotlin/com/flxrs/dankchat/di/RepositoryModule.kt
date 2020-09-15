package com.flxrs.dankchat.di

import com.flxrs.dankchat.service.ChatRepository
import com.flxrs.dankchat.service.DataRepository
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object RepositoryModule {

    @Singleton
    @Provides
    fun provideDataRepository(twitchApi: TwitchApi, emoteManager: EmoteManager): DataRepository = DataRepository(twitchApi, emoteManager)

    @Singleton
    @Provides
    fun provideChatRepository(twitchApi: TwitchApi, emoteManager: EmoteManager): ChatRepository = ChatRepository(twitchApi, emoteManager)
}