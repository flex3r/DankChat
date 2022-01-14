package com.flxrs.dankchat.service.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [EmoteUsageEntity::class], version = 1)
@TypeConverters(InstantConverter::class)
abstract class DankChatDatabase : RoomDatabase() {
    abstract fun emoteUsageDao(): EmoteUsageDao
}