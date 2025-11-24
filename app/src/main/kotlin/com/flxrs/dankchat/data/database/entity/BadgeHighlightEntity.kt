package com.flxrs.dankchat.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "badge_highlight")
data class BadgeHighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val enabled: Boolean,
    val badgeName: String,
    val isCustom: Boolean,

    @ColumnInfo(name = "create_notification")
    val createNotification: Boolean = false,

    @ColumnInfo(name = "custom_color")
    val customColor: Int? = null
)
