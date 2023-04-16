package com.flxrs.dankchat.di

import android.content.Context
import androidx.room.Room
import com.flxrs.dankchat.data.database.*
import com.flxrs.dankchat.data.database.dao.*
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
        .addMigrations(DankChatDatabase.MIGRATION_4_5)
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

    @Singleton
    @Provides
    fun provideUserDisplayDao(
        database: DankChatDatabase
    ): UserDisplayDao = database.userDisplayDao()

    @Singleton
    @Provides
    fun provideMessageHighlightDao(
        database: DankChatDatabase
    ): MessageHighlightDao = database.messageHighlightDao()

    @Singleton
    @Provides
    fun provideUserHighlightDao(
        database: DankChatDatabase
    ): UserHighlightDao = database.userHighlightDao()

    @Singleton
    @Provides
    fun provideIgnoreUserDao(
        database: DankChatDatabase
    ): UserIgnoreDao = database.userIgnoreDao()

    @Singleton
    @Provides
    fun provideMessageIgnoreDao(
        database: DankChatDatabase
    ): MessageIgnoreDao = database.messageIgnoreDao()

    @Singleton
    @Provides
    fun provideBlacklistedUserHighlightDao(
        database: DankChatDatabase
    ): BlacklistedUserDao = database.blacklistedUserDao()

    private const val DB_NAME = "dankchat-db"
}
