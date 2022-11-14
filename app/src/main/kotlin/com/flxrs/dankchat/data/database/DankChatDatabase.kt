package com.flxrs.dankchat.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.flxrs.dankchat.data.database.converter.InstantConverter
import com.flxrs.dankchat.data.database.dao.*
import com.flxrs.dankchat.data.database.entity.*

@Database(
    version = 5,
    entities = [
        EmoteUsageEntity::class,
        UploadEntity::class,
        MessageHighlightEntity::class,
        MessageIgnoreEntity::class,
        UserHighlightEntity::class,
        UserIgnoreEntity::class,
        BlacklistedUserEntity::class,
        UserDisplayEntity::class,
    ],
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class DankChatDatabase : RoomDatabase() {
    abstract fun emoteUsageDao(): EmoteUsageDao
    abstract fun recentUploadsDao(): RecentUploadsDao
    abstract fun userDisplayDao(): UserDisplayDao
    abstract fun messageHighlightDao(): MessageHighlightDao
    abstract fun userHighlightDao(): UserHighlightDao
    abstract fun userIgnoreDao(): UserIgnoreDao
    abstract fun messageIgnoreDao(): MessageIgnoreDao
    abstract fun blacklistedUserDao(): BlacklistedUserDao
}