package com.flxrs.dankchat.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    version = 3,
    entities = [EmoteUsageEntity::class, UploadEntity::class, UserDisplayEntity::class],
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class DankChatDatabase : RoomDatabase() {
    abstract fun emoteUsageDao(): EmoteUsageDao
    abstract fun recentUploadsDao(): RecentUploadsDao
    abstract fun userDisplayDao(): UserDisplayDao
}