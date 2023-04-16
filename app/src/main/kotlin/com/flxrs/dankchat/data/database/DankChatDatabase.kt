package com.flxrs.dankchat.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flxrs.dankchat.data.database.converter.InstantConverter
import com.flxrs.dankchat.data.database.dao.*
import com.flxrs.dankchat.data.database.entity.*

@Database(
    version = 6,
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
        AutoMigration(from = 5, to = 6),
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

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE user_highlight ADD COLUMN create_notification INTEGER DEFAULT 1 NOT NUll")
                database.execSQL("ALTER TABLE message_highlight ADD COLUMN create_notification INTEGER DEFAULT 0 NOT NUll")
                database.execSQL("UPDATE message_highlight SET create_notification=1 WHERE type = 'Username' OR type = 'Custom'")
            }
        }
    }
}
