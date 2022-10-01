package com.flxrs.dankchat.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blacklisted_user")
data class BlacklistedUserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val enabled: Boolean,
    val username: String,

    @ColumnInfo(name = "is_regex")
    val isRegex: Boolean
)
