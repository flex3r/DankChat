package com.flxrs.dankchat.di

import android.content.Context
import androidx.room.Room
import com.flxrs.dankchat.data.database.DankChatDatabase
import com.flxrs.dankchat.data.database.EmoteUsageDao
import com.flxrs.dankchat.data.database.RecentUploadsDao
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

    @Singleton
    @Provides
    fun provideRecentUploadsDao(
        database: DankChatDatabase
    ): RecentUploadsDao = database.recentUploadsDao()


    private const val DB_NAME = "dankchat-db"
}