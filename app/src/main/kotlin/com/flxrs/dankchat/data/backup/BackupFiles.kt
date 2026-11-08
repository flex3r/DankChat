package com.flxrs.dankchat.data.backup

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.database.entity.BadgeHighlightEntity
import com.flxrs.dankchat.data.database.entity.BlacklistedUserEntity
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntity
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntity
import com.flxrs.dankchat.data.database.entity.UserDisplayEntity
import com.flxrs.dankchat.data.database.entity.UserHighlightEntity
import com.flxrs.dankchat.data.database.entity.UserIgnoreEntity
import com.flxrs.dankchat.preferences.appearance.AppearanceSettings
import com.flxrs.dankchat.preferences.battery.BatterySettings
import com.flxrs.dankchat.preferences.chat.ChatSettings
import com.flxrs.dankchat.preferences.developer.DeveloperSettings
import com.flxrs.dankchat.preferences.model.ChannelWithRename
import com.flxrs.dankchat.preferences.notifications.NotificationsSettings
import com.flxrs.dankchat.preferences.stream.StreamsSettings
import com.flxrs.dankchat.preferences.tools.ToolsSettings
import kotlinx.serialization.Serializable

@Serializable
data class BackupChannel(
    val name: UserName,
    val rename: UserName? = null,
) {
    fun toChannelWithRename() = ChannelWithRename(channel = name, rename = rename)
}

/** Settings backups decode as a channel list as well, since both share [version] and [channels]. */
@Serializable
data class ChannelListBackup(
    val version: Int,
    val channels: List<BackupChannel>,
)

/** Everything except the login. [appVersion] is required so a channel list never decodes as a settings backup. */
@Serializable
data class SettingsBackup(
    val version: Int,
    val appVersion: String,
    val channels: List<BackupChannel> = emptyList(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val chat: ChatSettings = ChatSettings(),
    val notifications: NotificationsSettings = NotificationsSettings(),
    val streams: StreamsSettings = StreamsSettings(),
    val battery: BatterySettings = BatterySettings(),
    val tools: ToolsSettings = ToolsSettings(),
    val developer: DeveloperSettings = DeveloperSettings(),
    val messageHighlights: List<MessageHighlightEntity> = emptyList(),
    val userHighlights: List<UserHighlightEntity> = emptyList(),
    val badgeHighlights: List<BadgeHighlightEntity> = emptyList(),
    val highlightBlacklist: List<BlacklistedUserEntity> = emptyList(),
    val messageIgnores: List<MessageIgnoreEntity> = emptyList(),
    val userIgnores: List<UserIgnoreEntity> = emptyList(),
    val userDisplays: List<UserDisplayEntity> = emptyList(),
)
