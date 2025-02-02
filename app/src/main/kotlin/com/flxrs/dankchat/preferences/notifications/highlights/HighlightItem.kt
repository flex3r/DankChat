package com.flxrs.dankchat.preferences.notifications.highlights

import com.flxrs.dankchat.data.database.entity.BlacklistedUserEntity
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntity
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntityType
import com.flxrs.dankchat.data.database.entity.UserHighlightEntity
import kotlinx.collections.immutable.ImmutableList

sealed interface HighlightItem {
    val id: Long
}

data class MessageHighlightItem(
    override val id: Long,
    val enabled: Boolean,
    val type: Type,
    val pattern: String,
    val isRegex: Boolean,
    val isCaseSensitive: Boolean,
    val createNotification: Boolean,
    val loggedIn: Boolean,
    val notificationsEnabled: Boolean,
) : HighlightItem {
    enum class Type {
        Username,
        Subscription,
        Announcement,
        ChannelPointRedemption,
        FirstMessage,
        ElevatedMessage,
        Reply,
        Custom
    }

    val canNotify = type in WITH_NOTIFIES

    companion object {
        private val WITH_NOTIFIES = listOf(Type.Username, Type.Custom, Type.Reply)
    }
}

data class UserHighlightItem(
    override val id: Long,
    val enabled: Boolean,
    val username: String,
    val createNotification: Boolean,
    val notificationsEnabled: Boolean,
) : HighlightItem

data class BlacklistedUserItem(
    override val id: Long,
    val enabled: Boolean,
    val username: String,
    val isRegex: Boolean,
) : HighlightItem

fun MessageHighlightEntity.toItem(loggedIn: Boolean, notificationsEnabled: Boolean) = MessageHighlightItem(
    id = id,
    enabled = enabled,
    type = type.toItemType(),
    pattern = pattern,
    isRegex = isRegex,
    isCaseSensitive = isCaseSensitive,
    createNotification = createNotification,
    loggedIn = loggedIn,
    notificationsEnabled = notificationsEnabled,
)

fun MessageHighlightItem.toEntity() = MessageHighlightEntity(
    id = id,
    enabled = enabled,
    type = type.toEntityType(),
    pattern = pattern,
    isRegex = isRegex,
    isCaseSensitive = isCaseSensitive,
    createNotification = createNotification,
)

fun MessageHighlightItem.Type.toEntityType(): MessageHighlightEntityType = when (this) {
    MessageHighlightItem.Type.Username               -> MessageHighlightEntityType.Username
    MessageHighlightItem.Type.Subscription           -> MessageHighlightEntityType.Subscription
    MessageHighlightItem.Type.Announcement           -> MessageHighlightEntityType.Announcement
    MessageHighlightItem.Type.ChannelPointRedemption -> MessageHighlightEntityType.ChannelPointRedemption
    MessageHighlightItem.Type.FirstMessage           -> MessageHighlightEntityType.FirstMessage
    MessageHighlightItem.Type.ElevatedMessage        -> MessageHighlightEntityType.ElevatedMessage
    MessageHighlightItem.Type.Reply                  -> MessageHighlightEntityType.Reply
    MessageHighlightItem.Type.Custom                 -> MessageHighlightEntityType.Custom
}

fun MessageHighlightEntityType.toItemType(): MessageHighlightItem.Type = when (this) {
    MessageHighlightEntityType.Username               -> MessageHighlightItem.Type.Username
    MessageHighlightEntityType.Subscription           -> MessageHighlightItem.Type.Subscription
    MessageHighlightEntityType.Announcement           -> MessageHighlightItem.Type.Announcement
    MessageHighlightEntityType.ChannelPointRedemption -> MessageHighlightItem.Type.ChannelPointRedemption
    MessageHighlightEntityType.FirstMessage           -> MessageHighlightItem.Type.FirstMessage
    MessageHighlightEntityType.ElevatedMessage        -> MessageHighlightItem.Type.ElevatedMessage
    MessageHighlightEntityType.Reply                  -> MessageHighlightItem.Type.Reply
    MessageHighlightEntityType.Custom                 -> MessageHighlightItem.Type.Custom
}

fun UserHighlightEntity.toItem(notificationsEnabled: Boolean) = UserHighlightItem(
    id = id,
    enabled = enabled,
    username = username,
    createNotification = createNotification,
    notificationsEnabled = notificationsEnabled,
)

fun UserHighlightItem.toEntity() = UserHighlightEntity(
    id = id,
    enabled = enabled,
    username = username,
    createNotification = createNotification,
)

fun BlacklistedUserEntity.toItem() = BlacklistedUserItem(
    id = id,
    enabled = enabled,
    username = username,
    isRegex = isRegex,
)

fun BlacklistedUserItem.toEntity() = BlacklistedUserEntity(
    id = id,
    enabled = enabled,
    username = username,
    isRegex = isRegex,
)
