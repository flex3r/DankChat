package com.flxrs.dankchat.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

private val logger = KotlinLogging.logger("UserIgnoreEntity")

@Serializable
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
            val options =
                when {
                    isCaseSensitive -> emptySet()
                    else -> setOf(RegexOption.IGNORE_CASE)
                }
            username.toRegex(options)
        }.getOrElse {
            logger.error(it) { "Failed to create regex for username $username" }
            null
        }
    }
}
