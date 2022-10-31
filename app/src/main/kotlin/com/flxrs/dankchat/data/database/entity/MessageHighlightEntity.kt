package com.flxrs.dankchat.data.database.entity

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

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
    @ColumnInfo(name = "custom_color")
    val customColor: Int? = null
) {

    @delegate:Ignore
    val regex: Regex? by lazy {
        runCatching {
            val options = when {
                isCaseSensitive -> emptySet()
                else            -> setOf(RegexOption.IGNORE_CASE)
            }
            when {
                isRegex -> pattern.toRegex(options)
                else    -> "\\b$pattern\\b".toRegex(options)
            }

        }.getOrElse {
            Log.e(TAG, "Failed to create regex for pattern $pattern", it)
            null
        }
    }

    companion object {
        private val TAG = MessageHighlightEntity::class.java.simpleName
    }
}

// TODO webchat detection
enum class MessageHighlightEntityType {
    Username,
    Subscription,
    ChannelPointRedemption,
    FirstMessage,
    ElevatedMessage,
    Custom
}