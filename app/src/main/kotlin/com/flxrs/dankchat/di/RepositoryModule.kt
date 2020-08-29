package com.flxrs.dankchat.di

import com.flxrs.dankchat.service.ChatRepository
import com.flxrs.dankchat.service.DataRepository
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
    fun provideDataRepository(): DataRepository = DataRepository()

    @Singleton
    @Provides
    fun provideChatRepository(): ChatRepository = ChatRepository()
}