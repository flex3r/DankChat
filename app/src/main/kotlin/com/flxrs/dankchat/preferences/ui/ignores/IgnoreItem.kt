package com.flxrs.dankchat.preferences.ui.ignores

import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntity
import com.flxrs.dankchat.data.database.entity.UserIgnoreEntity
import com.flxrs.dankchat.data.repo.IgnoresRepository

sealed class IgnoreItem {
    abstract val id: Long
}

object AddItem : IgnoreItem() {
    override val id = -1L
}

data class MessageIgnoreItem(
    override val id: Long,
    var enabled: Boolean,
    var pattern: String,
    var isRegex: Boolean,
    var isCaseSensitive: Boolean,
    var isBlockMessage: Boolean,
    var replacement: String,
) : IgnoreItem()

data class UserIgnoreItem(
    override val id: Long,
    var enabled: Boolean,
    var username: String,
    var isRegex: Boolean,
    var isCaseSensitive: Boolean
) : IgnoreItem()

data class TwitchBlockItem(
    override val id: Long,
    val username: String,
    val userId: String,
) : IgnoreItem()

fun MessageIgnoreEntity.toItem() = MessageIgnoreItem(
    id = id,
    enabled = enabled,
    pattern = pattern,
    isRegex = isRegex,
    isCaseSensitive = isCaseSensitive,
    isBlockMessage = isBlockMessage,
    replacement = replacement ?: ""
)

fun MessageIgnoreItem.toEntity() = MessageIgnoreEntity(
    id = id,
    enabled = enabled,
    pattern = pattern,
    isRegex = isRegex,
    isCaseSensitive = isCaseSensitive,
    isBlockMessage = isBlockMessage,
    replacement = when {
        isBlockMessage -> null
        else           -> replacement
    }
)

fun UserIgnoreEntity.toItem() = UserIgnoreItem(
    id = id,
    enabled = enabled,
    username = username,
    isRegex = isRegex,
    isCaseSensitive = isCaseSensitive,
)

fun UserIgnoreItem.toEntity() = UserIgnoreEntity(
    id = id,
    enabled = enabled,
    username = username,
    isRegex = isRegex,
    isCaseSensitive = isCaseSensitive
)

fun IgnoresRepository.TwitchBlock.toItem() = TwitchBlockItem(
    id = id.hashCode().toLong(),
    userId = id,
    username = name,
)