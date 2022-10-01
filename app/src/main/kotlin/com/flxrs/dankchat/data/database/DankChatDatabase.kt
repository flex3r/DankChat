package com.flxrs.dankchat.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.flxrs.dankchat.data.database.converter.InstantConverter
import com.flxrs.dankchat.data.database.entity.*

@Database(
    version = 3,
    entities = [
        EmoteUsageEntity::class,
        UploadEntity::class,
        MessageHighlightEntity::class,
        MessageIgnoreEntity::class,
        UserHighlightEntity::class,
        BlacklistedUserEntity::class
    ],
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3)
    ],
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class DankChatDatabase : RoomDatabase() {
    abstract fun emoteUsageDao(): EmoteUsageDao
    abstract fun recentUploadsDao(): RecentUploadsDao
}