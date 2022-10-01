package com.flxrs.dankchat.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_highlight")
data class MessageHighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val enabled: Boolean,
    val type: MessageHighlightType,
    val pattern: String,

    @ColumnInfo(name = "is_regex")
    val isRegex: Boolean,
    @ColumnInfo(name = "is_case_sensitive")
    val isCaseSensitive: Boolean,
    @ColumnInfo(name = "custom_color")
    val customColor: Int?
)

enum class MessageHighlightType {
    Username,
    Subscription,
    ChannelPointRedemption,
    FirstMessage,
    Custom
}