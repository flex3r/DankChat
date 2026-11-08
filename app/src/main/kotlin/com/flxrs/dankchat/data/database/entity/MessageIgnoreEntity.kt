package com.flxrs.dankchat.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

private val logger = KotlinLogging.logger("MessageIgnoreEntity")

@Serializable
@Entity(tableName = "message_ignore")
data class MessageIgnoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val enabled: Boolean,
    val type: MessageIgnoreEntityType,
    val pattern: String,
    @ColumnInfo(name = "is_regex")
    val isRegex: Boolean = false,
    @ColumnInfo(name = "is_case_sensitive")
    val isCaseSensitive: Boolean = false,
    @ColumnInfo(name = "is_block_message")
    val isBlockMessage: Boolean = false,
    val replacement: String? = null,
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

    @delegate:Ignore
    val escapedReplacement: String? by lazy {
        replacement?.let {
            Regex.escapeReplacement(it)
        }
    }
}

enum class MessageIgnoreEntityType {
    Subscription,
    Announcement,
    WatchStreak,
    ChannelPointRedemption,
    FirstMessage,
    ElevatedMessage,
    Custom,
}
