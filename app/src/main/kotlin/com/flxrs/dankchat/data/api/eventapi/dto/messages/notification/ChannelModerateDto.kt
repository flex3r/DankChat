package com.flxrs.dankchat.data.api.eventapi.dto.messages.notification

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("channel.moderate")
data class ChannelModerateDto(
    @SerialName("broadcaster_user_id")
    val broadcasterUserId: UserId,
    @SerialName("broadcaster_user_login")
    val broadcasterUserLogin: UserName,
    @SerialName("broadcaster_user_name")
    val broadcasterUserName: DisplayName,
    @SerialName("source_broadcaster_user_id")
    val sourceBroadcasterUserId: UserId? = null,
    @SerialName("source_broadcaster_user_login")
    val sourceBroadcasterUserLogin: UserName? = null,
    @SerialName("source_broadcaster_user_name")
    val sourceBroadcasterUserName: DisplayName? = null,
    @SerialName("moderator_user_id")
    val moderatorUserId: UserId,
    @SerialName("moderator_user_login")
    val moderatorUserLogin: UserName,
    @SerialName("moderator_user_name")
    val moderatorUserName: DisplayName,
    @SerialName("action")
    val action: ChannelModerateAction = ChannelModerateAction.Unknown,
    @SerialName("followers")
    val followers: FollowersMetadata? = null,
    @SerialName("slow")
    val slow: SlowMetadata? = null,
    @SerialName("vip")
    val vip: VipMetadata? = null,
    @SerialName("unvip")
    val unvip: UnvipMetadata? = null,
    @SerialName("mod")
    val mod: ModMetadata? = null,
    @SerialName("unmod")
    val unmod: UnmodMetadata? = null,
    @SerialName("ban")
    val ban: BanMetadata? = null,
    @SerialName("unban")
    val unban: UnbanMetadata? = null,
    @SerialName("timeout")
    val timeout: TimeoutMetadata? = null,
    @SerialName("untimeout")
    val untimeout: UntimeoutMetadata? = null,
    @SerialName("raid")
    val raid: RaidMetadata? = null,
    @SerialName("unraid")
    val unraid: UnraidMetadata? = null,
    @SerialName("delete")
    val delete: DeleteMetadata? = null,
    @SerialName("automod_terms")
    val automodTerms: AutomodTermsMetadata? = null,
    @SerialName("unban_request")
    val unbanRequest: UnbanRequestMetadata? = null,
    @SerialName("warn")
    val warn: WarnMetadata? = null,
    @SerialName("shared_chat_ban")
    val sharedChatBan: BanMetadata? = null,
    @SerialName("shared_chat_unban")
    val sharedChatUnban: UnbanMetadata? = null,
    @SerialName("shared_chat_timeout")
    val sharedChatTimeout: TimeoutMetadata? = null,
    @SerialName("shared_chat_untimeout")
    val sharedChatUntimeout: UntimeoutMetadata? = null,
    @SerialName("shared_chat_delete")
    val sharedChatDelete: DeleteMetadata? = null,
) : NotificationEventDto

@Serializable
enum class ChannelModerateAction {
    @SerialName("ban")
    Ban,
    @SerialName("timeout")
    Timeout,
    @SerialName("unban")
    Unban,
    @SerialName("untimeout")
    Untimeout,
    @SerialName("clear")
    Clear,
    @SerialName("emoteonly")
    EmoteOnly,
    @SerialName("emoteonlyoff")
    EmoteOnlyOff,
    @SerialName("followers")
    Followers,
    @SerialName("followersoff")
    FollowersOff,
    @SerialName("uniquechat")
    UniqueChat,
    @SerialName("uniquechatoff")
    UniqueChatOff,
    @SerialName("slow")
    Slow,
    @SerialName("slowoff")
    SlowOff,
    @SerialName("subscribers")
    Subscribers,
    @SerialName("subscribersoff")
    SubscribersOff,
    @SerialName("unraid")
    Unraid,
    @SerialName("delete")
    Delete,
    @SerialName("unvip")
    Unvip,
    @SerialName("vip")
    Vip,
    @SerialName("raid")
    Raid,
    @SerialName("add_blocked_term")
    AddBlockedTerm,
    @SerialName("add_permitted_term")
    AddPermittedTerm,
    @SerialName("remove_blocked_term")
    RemoveBlockedTerm,
    @SerialName("remove_permitted_term")
    RemovePermittedTerm,
    @SerialName("mod")
    Mod,
    @SerialName("unmod")
    Unmod,
    @SerialName("approve_unban_request")
    ApproveUnbanRequest,
    @SerialName("deny_unban_request")
    DenyUnbanRequest,
    @SerialName("warn")
    Warn,
    @SerialName("shared_chat_ban")
    SharedChatBan,
    @SerialName("shared_chat_timeout")
    SharedChatTimeout,
    @SerialName("shared_chat_unban")
    SharedChatUnban,
    @SerialName("shared_chat_untimeout")
    SharedChatUntimeout,
    @SerialName("shared_chat_delete")
    SharedChatDelete,
    Unknown,
}

@Serializable
data class FollowersMetadata(
    @SerialName("follow_duration_minutes")
    val followDurationMinutes: Int,
)

@Serializable
data class SlowMetadata(
    @SerialName("wait_time_seconds")
    val waitTimeSeconds: Int,
)

@Serializable
data class VipMetadata(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
)

@Serializable
data class UnvipMetadata(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
)

@Serializable
data class ModMetadata(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
)

@Serializable
data class UnmodMetadata(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
)

@Serializable
data class BanMetadata(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
    @SerialName("reason")
    val reason: String? = null,
)

@Serializable
data class UnbanMetadata(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
)

@Serializable
data class TimeoutMetadata(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
    @SerialName("reason")
    val reason: String? = null,
    @SerialName("expires_at")
    val expiresAt: Instant,
)

@Serializable
data class UntimeoutMetadata(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
)

@Serializable
data class RaidMetadata(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
    @SerialName("viewer_count")
    val viewerCount: Int,
)

@Serializable
data class UnraidMetadata(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
)

@Serializable
data class DeleteMetadata(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("message_body")
    val messageBody: String,
)

@Serializable
data class AutomodTermsMetadata(
    @SerialName("action")
    val action: String,
    @SerialName("list")
    val list: String,
    @SerialName("terms")
    val terms: List<String>,
    @SerialName("from_automod")
    val fromAutomod: Boolean,
)

@Serializable
data class UnbanRequestMetadata(
    @SerialName("is_approved")
    val isApproved: Boolean,
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
    @SerialName("moderator_message")
    val moderatorMessage: String,
)

@Serializable
data class WarnMetadata(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("user_login")
    val userLogin: UserName,
    @SerialName("user_name")
    val userName: DisplayName,
    @SerialName("reason")
    val reason: String? = null,
    @SerialName("chat_rules_cited")
    val chatRulesCited: List<String>? = null,
)
