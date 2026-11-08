package com.flxrs.dankchat.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

private val logger = KotlinLogging.logger("BlacklistedUserEntity")

@Serializable
@Entity(tableName = "blacklisted_user_highlight")
data class BlacklistedUserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val enabled: Boolean,
    val username: String,
    @ColumnInfo(name = "is_regex")
    val isRegex: Boolean = false,
) {
    @delegate:Ignore
    val regex: Regex? by lazy {
        runCatching {
            username.toRegex(RegexOption.IGNORE_CASE)
        }.getOrElse {
            logger.error(it) { "Failed to create regex for username $username" }
            null
        }
    }
}
