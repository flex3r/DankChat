package com.flxrs.dankchat.preferences.ui.highlights

import com.flxrs.dankchat.data.database.entity.MessageHighlightEntity
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntityType
import com.flxrs.dankchat.data.database.entity.UserHighlightEntity

sealed class HighlightItem {
    abstract val id: Long
}

object AddItem : HighlightItem() {
    override val id = -1L
}

data class MessageHighlightItem(
    override val id: Long,
    var enabled: Boolean,
    val type: Type,
    var pattern: String,
    var isRegex: Boolean,
    var isCaseSensitive: Boolean,
) : HighlightItem() {
    enum class Type {
        Username,
        Subscription,
        ChannelPointRedemption,
        FirstMessage,
        ElevatedMessage,
        Custom
    }
}

data class UserHighlightItem(
    override val id: Long,
    var enabled: Boolean,
    var username: String
) : HighlightItem()


fun MessageHighlightEntity.toItem() = MessageHighlightItem(
    id = id,
    enabled = enabled,
    type = type.toItemType(),
    pattern = pattern,
    isRegex = isRegex,
    isCaseSensitive = isCaseSensitive
)

fun MessageHighlightItem.toEntity() = MessageHighlightEntity(
    id = id,
    enabled = enabled,
    type = type.toEntityType(),
    pattern = pattern,
    isRegex = isRegex,
    isCaseSensitive = isCaseSensitive
)

fun MessageHighlightItem.Type.toEntityType(): MessageHighlightEntityType = when (this) {
    MessageHighlightItem.Type.Username               -> MessageHighlightEntityType.Username
    MessageHighlightItem.Type.Subscription           -> MessageHighlightEntityType.Subscription
    MessageHighlightItem.Type.ChannelPointRedemption -> MessageHighlightEntityType.ChannelPointRedemption
    MessageHighlightItem.Type.FirstMessage           -> MessageHighlightEntityType.FirstMessage
    MessageHighlightItem.Type.ElevatedMessage        -> MessageHighlightEntityType.ElevatedMessage
    MessageHighlightItem.Type.Custom                 -> MessageHighlightEntityType.Custom
}

fun MessageHighlightEntityType.toItemType(): MessageHighlightItem.Type = when (this) {
    MessageHighlightEntityType.Username               -> MessageHighlightItem.Type.Username
    MessageHighlightEntityType.Subscription           -> MessageHighlightItem.Type.Subscription
    MessageHighlightEntityType.ChannelPointRedemption -> MessageHighlightItem.Type.ChannelPointRedemption
    MessageHighlightEntityType.FirstMessage           -> MessageHighlightItem.Type.FirstMessage
    MessageHighlightEntityType.ElevatedMessage        -> MessageHighlightItem.Type.ElevatedMessage
    MessageHighlightEntityType.Custom                 -> MessageHighlightItem.Type.Custom
}

fun UserHighlightEntity.toItem() = UserHighlightItem(
    id = id,
    enabled = enabled,
    username = username
)

fun UserHighlightItem.toEntity() = UserHighlightEntity(
    id = id,
    enabled = enabled,
    username = username
)
