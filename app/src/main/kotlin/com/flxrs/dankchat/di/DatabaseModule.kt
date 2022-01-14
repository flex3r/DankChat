package com.flxrs.dankchat.di

import android.content.Context
import androidx.room.Room
import com.flxrs.dankchat.service.database.DankChatDatabase
import com.flxrs.dankchat.service.database.EmoteUsageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context
    ): DankChatDatabase = Room
        .databaseBuilder(context, DankChatDatabase::class.java, DB_NAME)
        .build()

    @Singleton
    @Provides
    fun provideEmoteUsageDao(
        database: DankChatDatabase
    ): EmoteUsageDao = database.emoteUsageDao()


    private const val DB_NAME = "dankchat-db"
}