package com.flxrs.dankchat.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.flxrs.dankchat.data.database.entity.EmoteUsageEntity
import com.flxrs.dankchat.data.database.entity.UploadEntity

@Database(
    version = 2,
    entities = [EmoteUsageEntity::class, UploadEntity::class],
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class DankChatDatabase : RoomDatabase() {
    abstract fun emoteUsageDao(): EmoteUsageDao
    abstract fun recentUploadsDao(): RecentUploadsDao
}