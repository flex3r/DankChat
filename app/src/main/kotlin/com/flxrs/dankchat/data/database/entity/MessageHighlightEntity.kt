package com.flxrs.dankchat.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("MessageHighlightEntity")

@Entity(tableName = "message_highlight")
data class MessageHighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val enabled: Boolean,
    val type: MessageHighlightEntityType,
    val pattern: String,
    @ColumnInfo(name = "is_regex")
    val isRegex: Boolean = false,
    @ColumnInfo(name = "is_case_sensitive")
    val isCaseSensitive: Boolean = false,
    @ColumnInfo(name = "create_notification")
    val createNotification: Boolean = true,
    @ColumnInfo(name = "custom_color")
    val customColor: Int? = null,
) {
    @delegate:Ignore
    val regex: Regex? by lazy {
        runCatching {
            val options =
                when {
                    isCaseSensitive -> emptySet()
                    else -> setOf(RegexOption.IGNORE_CASE)
                }
            when {
                isRegex -> pattern.toRegex(options)
                else -> """(?<!\w)${Regex.escape(pattern)}(?!\w)""".toRegex(options)
            }
        }.getOrElse {
            logger.error(it) { "Failed to create regex for pattern $pattern" }
            null
        }
    }
}

// TODO webchat detection
enum class MessageHighlightEntityType {
    Username,
    Subscription,
    Announcement,
    WatchStreak,
    ChannelPointRedemption,
    FirstMessage,
    ElevatedMessage,
    Reply,
    InlineWhisper,
    Custom,
}
