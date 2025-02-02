package com.flxrs.dankchat.preferences.notifications.ignores

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntity
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntityType
import com.flxrs.dankchat.data.database.entity.UserIgnoreEntity
import com.flxrs.dankchat.data.repo.IgnoresRepository

sealed interface IgnoreItem {
    val id: Long
}

data class MessageIgnoreItem(
    override val id: Long,
    val type: Type,
    val enabled: Boolean,
    val pattern: String,
    val isRegex: Boolean,
    val isCaseSensitive: Boolean,
    val isBlockMessage: Boolean,
    val replacement: String,
) : IgnoreItem {
    enum class Type {
        Subscription,
        Announcement,
        ChannelPointRedemption,
        FirstMessage,
        ElevatedMessage,
        Custom
    }
}

data class UserIgnoreItem(
    override val id: Long,
    val enabled: Boolean,
    val username: String,
    val isRegex: Boolean,
    val isCaseSensitive: Boolean
) : IgnoreItem

data class TwitchBlockItem(
    override val id: Long,
    val username: UserName,
    val userId: UserId,
) : IgnoreItem

fun MessageIgnoreEntity.toItem() = MessageIgnoreItem(
    id = id,
    type = type.toItemType(),
    enabled = enabled,
    pattern = pattern,
    isRegex = isRegex,
    isCaseSensitive = isCaseSensitive,
    isBlockMessage = isBlockMessage,
    replacement = replacement ?: ""
)

fun MessageIgnoreItem.toEntity() = MessageIgnoreEntity(
    id = id,
    type = type.toEntityType(),
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

fun MessageIgnoreItem.Type.toEntityType(): MessageIgnoreEntityType = when (this) {
    MessageIgnoreItem.Type.Subscription           -> MessageIgnoreEntityType.Subscription
    MessageIgnoreItem.Type.Announcement           -> MessageIgnoreEntityType.Announcement
    MessageIgnoreItem.Type.ChannelPointRedemption -> MessageIgnoreEntityType.ChannelPointRedemption
    MessageIgnoreItem.Type.FirstMessage           -> MessageIgnoreEntityType.FirstMessage
    MessageIgnoreItem.Type.ElevatedMessage        -> MessageIgnoreEntityType.ElevatedMessage
    MessageIgnoreItem.Type.Custom                 -> MessageIgnoreEntityType.Custom
}

fun MessageIgnoreEntityType.toItemType(): MessageIgnoreItem.Type = when (this) {
    MessageIgnoreEntityType.Subscription           -> MessageIgnoreItem.Type.Subscription
    MessageIgnoreEntityType.Announcement           -> MessageIgnoreItem.Type.Announcement
    MessageIgnoreEntityType.ChannelPointRedemption -> MessageIgnoreItem.Type.ChannelPointRedemption
    MessageIgnoreEntityType.FirstMessage           -> MessageIgnoreItem.Type.FirstMessage
    MessageIgnoreEntityType.ElevatedMessage        -> MessageIgnoreItem.Type.ElevatedMessage
    MessageIgnoreEntityType.Custom                 -> MessageIgnoreItem.Type.Custom
}

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
