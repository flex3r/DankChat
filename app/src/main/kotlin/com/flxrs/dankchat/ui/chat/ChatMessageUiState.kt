package com.flxrs.dankchat.ui.chat

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.ui.chat.messages.common.LinkUi
import com.flxrs.dankchat.utils.TextResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
sealed interface ChatMessageUiState {
    val id: String
    val tag: Int // Used for invalidating/updating messages when emotes/badges change
    val timestamp: String
    val lightBackgroundColor: Color
    val darkBackgroundColor: Color
    val textAlpha: Float
    val enableRipple: Boolean
    val isHighlighted: Boolean
    val roundedTopCorners: Boolean
    val roundedBottomCorners: Boolean
    val showDividerBelow: Boolean

    @Immutable
    data class PrivMessageUi(
        override val id: String,
        override val tag: Int,
        override val timestamp: String,
        override val lightBackgroundColor: Color,
        override val darkBackgroundColor: Color,
        override val textAlpha: Float,
        override val enableRipple: Boolean,
        override val isHighlighted: Boolean,
        override val roundedTopCorners: Boolean = false,
        override val roundedBottomCorners: Boolean = false,
        override val showDividerBelow: Boolean = false,
        val channel: UserName,
        val userId: UserId?,
        val userName: UserName,
        val displayName: DisplayName,
        val badges: ImmutableList<BadgeUi>,
        val rawNameColor: Int,
        val nameText: String,
        val message: String,
        val links: ImmutableList<LinkUi>,
        val emotes: ImmutableList<EmoteUi>,
        val isAction: Boolean,
        val thread: ThreadUi?,
        val highlightHeader: TextResource? = null,
        val highlightHeaderImageUrl: String? = null,
        val highlightHeaderCost: Int? = null,
        val highlightHeaderCostSuffix: String? = null,
        val fullMessage: String, // For copying
    ) : ChatMessageUiState

    @Immutable
    data class SystemMessageUi(
        override val id: String,
        override val tag: Int,
        override val timestamp: String,
        override val lightBackgroundColor: Color,
        override val darkBackgroundColor: Color,
        override val textAlpha: Float,
        override val enableRipple: Boolean = false,
        override val isHighlighted: Boolean = false,
        override val roundedTopCorners: Boolean = false,
        override val roundedBottomCorners: Boolean = false,
        override val showDividerBelow: Boolean = false,
        val message: TextResource,
        val boldText: String? = null,
    ) : ChatMessageUiState

    @Immutable
    data class NoticeMessageUi(
        override val id: String,
        override val tag: Int,
        override val timestamp: String,
        override val lightBackgroundColor: Color,
        override val darkBackgroundColor: Color,
        override val textAlpha: Float,
        override val enableRipple: Boolean = false,
        override val isHighlighted: Boolean = false,
        override val roundedTopCorners: Boolean = false,
        override val roundedBottomCorners: Boolean = false,
        override val showDividerBelow: Boolean = false,
        val message: String,
        val links: ImmutableList<LinkUi> = persistentListOf(),
    ) : ChatMessageUiState

    @Immutable
    data class UserNoticeMessageUi(
        override val id: String,
        override val tag: Int,
        override val timestamp: String,
        override val lightBackgroundColor: Color,
        override val darkBackgroundColor: Color,
        override val textAlpha: Float,
        override val enableRipple: Boolean = false,
        override val isHighlighted: Boolean = false,
        override val roundedTopCorners: Boolean = false,
        override val roundedBottomCorners: Boolean = false,
        override val showDividerBelow: Boolean = false,
        val channel: UserName,
        val userId: UserId? = null,
        val userName: UserName? = null,
        val message: String,
        val links: ImmutableList<LinkUi> = persistentListOf(),
        val displayName: String = "",
        val rawNameColor: Int = Message.DEFAULT_COLOR,
        val shouldHighlight: Boolean,
    ) : ChatMessageUiState

    @Immutable
    data class ModerationMessageUi(
        override val id: String,
        override val tag: Int,
        override val timestamp: String,
        override val lightBackgroundColor: Color,
        override val darkBackgroundColor: Color,
        override val textAlpha: Float,
        override val enableRipple: Boolean = false,
        override val isHighlighted: Boolean = false,
        override val roundedTopCorners: Boolean = false,
        override val roundedBottomCorners: Boolean = false,
        override val showDividerBelow: Boolean = false,
        val channel: UserName,
        val message: TextResource,
        val creatorName: String? = null,
        val targetName: String? = null,
        val creatorUserName: UserName? = null,
        val targetUserName: UserName? = null,
        val creatorColor: Int = Message.DEFAULT_COLOR,
        val targetColor: Int = Message.DEFAULT_COLOR,
        val arguments: ImmutableList<Any> = persistentListOf(),
    ) : ChatMessageUiState

    @Immutable
    data class PointRedemptionMessageUi(
        override val id: String,
        override val tag: Int,
        override val timestamp: String,
        override val lightBackgroundColor: Color,
        override val darkBackgroundColor: Color,
        override val textAlpha: Float,
        override val enableRipple: Boolean = false,
        override val isHighlighted: Boolean = true,
        override val roundedTopCorners: Boolean = false,
        override val roundedBottomCorners: Boolean = false,
        override val showDividerBelow: Boolean = false,
        val nameText: String?,
        val rawNameColor: Int,
        val title: String,
        val cost: Int,
        val rewardImageUrl: String,
        val requiresUserInput: Boolean,
    ) : ChatMessageUiState

    @Immutable
    data class DateSeparatorUi(
        override val id: String,
        override val tag: Int = 0,
        override val timestamp: String,
        override val lightBackgroundColor: Color = Color.Transparent,
        override val darkBackgroundColor: Color = Color.Transparent,
        override val textAlpha: Float = 0.5f,
        override val enableRipple: Boolean = false,
        override val isHighlighted: Boolean = false,
        override val roundedTopCorners: Boolean = false,
        override val roundedBottomCorners: Boolean = false,
        override val showDividerBelow: Boolean = false,
        val dateText: String,
    ) : ChatMessageUiState

    @Immutable
    data class AutomodMessageUi(
        override val id: String,
        override val tag: Int,
        override val timestamp: String,
        override val lightBackgroundColor: Color,
        override val darkBackgroundColor: Color,
        override val textAlpha: Float,
        override val enableRipple: Boolean = false,
        override val isHighlighted: Boolean = false,
        override val roundedTopCorners: Boolean = false,
        override val roundedBottomCorners: Boolean = false,
        override val showDividerBelow: Boolean = false,
        val heldMessageId: String,
        val channel: UserName,
        val badges: ImmutableList<BadgeUi>,
        val userDisplayName: String,
        val rawNameColor: Int,
        val messageText: String?,
        val reason: TextResource,
        val status: AutomodMessageStatus,
        val isUserSide: Boolean = false,
    ) : ChatMessageUiState {
        enum class AutomodMessageStatus { Pending, Approved, Denied, Expired }
    }

    @Immutable
    data class WhisperMessageUi(
        override val id: String,
        override val tag: Int,
        override val timestamp: String,
        override val lightBackgroundColor: Color,
        override val darkBackgroundColor: Color,
        override val textAlpha: Float,
        override val enableRipple: Boolean,
        override val isHighlighted: Boolean = false,
        override val roundedTopCorners: Boolean = false,
        override val roundedBottomCorners: Boolean = false,
        override val showDividerBelow: Boolean = false,
        val userId: UserId,
        val userName: UserName,
        val displayName: DisplayName,
        val recipientId: UserId?,
        val recipientUserName: UserName,
        val recipientDisplayName: DisplayName,
        val badges: ImmutableList<BadgeUi>,
        val rawSenderColor: Int,
        val rawRecipientColor: Int,
        val senderName: String,
        val recipientName: String,
        val message: String,
        val links: ImmutableList<LinkUi>,
        val emotes: ImmutableList<EmoteUi>,
        val fullMessage: String,
        val replyTargetName: UserName,
    ) : ChatMessageUiState
}

@Immutable
data class BadgeUi(
    val url: String,
    val badge: Badge,
    val position: Int, // Position in message
    val drawableResId: Int? = null,
)

@Immutable
data class EmoteUi(
    val code: String,
    val urls: ImmutableList<String>,
    val position: IntRange,
    val isAnimated: Boolean,
    val isTwitch: Boolean,
    val scale: Int,
    val emotes: ImmutableList<ChatMessageEmote>, // For click handling
    val cheerAmount: Int? = null,
    val cheerColor: Color? = null,
)

@Immutable
data class ThreadUi(
    val rootId: String,
    val userName: String,
    val message: String,
    val rawNameColor: Int = Message.DEFAULT_COLOR,
)
