package com.flxrs.dankchat.data.database.entity

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "blacklisted_user_highlight")
data class BlacklistedUserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val enabled: Boolean,
    val username: String,

    @ColumnInfo(name = "is_regex")
    val isRegex: Boolean = false
) {

    @delegate:Ignore
    val regex: Regex? by lazy {
        runCatching {
            username.toRegex(RegexOption.IGNORE_CASE)
        }.getOrElse {
            Log.e(TAG, "Failed to create regex for username $username", it)
            null
        }
    }

    companion object {
        private val TAG = BlacklistedUserEntity::class.java.simpleName
    }
}
