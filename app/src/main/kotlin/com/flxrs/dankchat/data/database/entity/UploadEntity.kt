package com.flxrs.dankchat.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "upload")
data class UploadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val timestamp: Instant,

    @ColumnInfo(name = "image_link")
    val imageLink: String,

    @ColumnInfo(name = "delete_link")
    val deleteLink: String?
)