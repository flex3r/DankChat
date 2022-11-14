package com.flxrs.dankchat.data.database.entity

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "blacklisted_user")
data class UserIgnoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val enabled: Boolean,
    val username: String,

    @ColumnInfo(name = "is_regex")
    val isRegex: Boolean = false,
    @ColumnInfo(name = "is_case_sensitive", defaultValue = "0")
    val isCaseSensitive: Boolean = false,
) {
    @delegate:Ignore
    val regex: Regex? by lazy {
        runCatching {
            val options = when {
                isCaseSensitive -> emptySet()
                else            -> setOf(RegexOption.IGNORE_CASE)
            }
            username.toRegex(options)
        }.getOrElse {
            Log.e(TAG, "Failed to create regex for username $username", it)
            null
        }
    }

    companion object {
        private val TAG = UserIgnoreEntity::class.java.simpleName
    }
}
