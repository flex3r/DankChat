package com.flxrs.dankchat.data.database.entity

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "message_ignore")
data class MessageIgnoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val enabled: Boolean,
    val pattern: String,

    @ColumnInfo(name = "is_regex")
    val isRegex: Boolean = false,
    @ColumnInfo(name = "is_case_sensitive")
    val isCaseSensitive: Boolean = false,
    val replacement: String? = null,
) {
    @delegate:Ignore
    val regex: Regex? by lazy {
        runCatching {
            val options = when {
                isCaseSensitive -> setOf(RegexOption.IGNORE_CASE)
                else            -> emptySet()
            }
            when {
                isRegex -> pattern.toRegex(options)
                else    -> Regex("\\b$pattern\\b")
            }
        }.getOrElse {
            Log.e(TAG, "Failed to create regex for pattern $pattern", it)
            null
        }
    }

    companion object {
        private val TAG = MessageIgnoreEntity::class.java.simpleName
    }
}