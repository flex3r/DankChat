package com.flxrs.dankchat.di

import android.content.Context
import androidx.room.Room
import com.flxrs.dankchat.data.database.DankChatDatabase
import com.flxrs.dankchat.data.database.dao.BlacklistedUserDao
import com.flxrs.dankchat.data.database.dao.EmoteUsageDao
import com.flxrs.dankchat.data.database.dao.MessageHighlightDao
import com.flxrs.dankchat.data.database.dao.MessageIgnoreDao
import com.flxrs.dankchat.data.database.dao.RecentUploadsDao
import com.flxrs.dankchat.data.database.dao.UserDisplayDao
import com.flxrs.dankchat.data.database.dao.UserHighlightDao
import com.flxrs.dankchat.data.database.dao.UserIgnoreDao
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class DatabaseModule {

    @Single
    fun provideDatabase(
        context: Context
    ): DankChatDatabase = Room
        .databaseBuilder(context, DankChatDatabase::class.java, DB_NAME)
        .addMigrations(DankChatDatabase.MIGRATION_4_5)
        .build()

    @Single
    fun provideEmoteUsageDao(
        database: DankChatDatabase
    ): EmoteUsageDao = database.emoteUsageDao()

    @Single
    fun provideRecentUploadsDao(
        database: DankChatDatabase
    ): RecentUploadsDao = database.recentUploadsDao()

    @Single
    fun provideUserDisplayDao(
        database: DankChatDatabase
    ): UserDisplayDao = database.userDisplayDao()

    @Single
    fun provideMessageHighlightDao(
        database: DankChatDatabase
    ): MessageHighlightDao = database.messageHighlightDao()

    @Single
    fun provideUserHighlightDao(
        database: DankChatDatabase
    ): UserHighlightDao = database.userHighlightDao()

    @Single
    fun provideIgnoreUserDao(
        database: DankChatDatabase
    ): UserIgnoreDao = database.userIgnoreDao()

    @Single
    fun provideMessageIgnoreDao(
        database: DankChatDatabase
    ): MessageIgnoreDao = database.messageIgnoreDao()

    @Single
    fun provideBlacklistedUserHighlightDao(
        database: DankChatDatabase
    ): BlacklistedUserDao = database.blacklistedUserDao()

    private companion object {
        const val DB_NAME = "dankchat-db"
    }
}
