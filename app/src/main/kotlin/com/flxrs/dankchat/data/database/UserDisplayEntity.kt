package com.flxrs.dankchat.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "user_display")
data class UserDisplayEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "target_user") val targetUser: String, // target user to apply the color and/or alias
    @ColumnInfo(name = "enabled") val enabled: Boolean,
    @ColumnInfo(name = "color_enabled") val colorEnabled: Boolean,
    @ColumnInfo(name = "color") val color: Int,
    @ColumnInfo(name = "aliasEnabled") val aliasEnabled: Boolean,
    @ColumnInfo(name = "alias") val alias: String? // aliased name
)
