package com.flxrs.dankchat.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_highlight")
data class UserHighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val enabled: Boolean,
    val username: String,

    @ColumnInfo(name = "custom_color")
    val customColor: Int? = null
)
