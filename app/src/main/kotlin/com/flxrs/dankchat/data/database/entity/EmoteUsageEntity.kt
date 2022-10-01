package com.flxrs.dankchat.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "emote_usage")
data class EmoteUsageEntity(
    @PrimaryKey
    @ColumnInfo(name = "emote_id")
    val emoteId: String,

    @ColumnInfo(name = "last_used")
    val lastUsed: Instant,
)
