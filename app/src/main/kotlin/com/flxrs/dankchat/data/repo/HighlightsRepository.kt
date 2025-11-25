@file:Suppress("DEPRECATION")

package com.flxrs.dankchat.data.repo

import android.util.Log
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.database.dao.BadgeHighlightDao
import com.flxrs.dankchat.data.database.dao.BlacklistedUserDao
import com.flxrs.dankchat.data.database.dao.MessageHighlightDao
import com.flxrs.dankchat.data.database.dao.UserHighlightDao
import com.flxrs.dankchat.data.database.entity.BadgeHighlightEntity
import com.flxrs.dankchat.data.database.entity.BlacklistedUserEntity
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntity
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntityType
import com.flxrs.dankchat.data.database.entity.UserHighlightEntity
import com.flxrs.dankchat.data.twitch.message.Highlight
import com.flxrs.dankchat.data.twitch.message.HighlightType
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.PointRedemptionMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.data.twitch.message.isAnnouncement
import com.flxrs.dankchat.data.twitch.message.isElevatedMessage
import com.flxrs.dankchat.data.twitch.message.isFirstMessage
import com.flxrs.dankchat.data.twitch.message.isReward
import com.flxrs.dankchat.data.twitch.message.isSub
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.notifications.NotificationsSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single
class HighlightsRepository(
    private val messageHighlightDao: MessageHighlightDao,
    private val userHighlightDao: UserHighlightDao,
    private val badgeHighlightDao: BadgeHighlightDao,
    private val blacklistedUserDao: BlacklistedUserDao,
    private val preferences: DankChatPreferenceStore,
    private val notificationsSettingsDataStore: NotificationsSettingsDataStore,
    dispatchersProvider: DispatchersProvider,
) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val currentUserAndDisplay = preferences.currentUserAndDisplayFlow.stateIn(coroutineScope, SharingStarted.Eagerly, null)
    private val currentUserRegex = currentUserAndDisplay
        .map(::createUserAndDisplayRegex)
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    val messageHighlights = messageHighlightDao.getMessageHighlightsFlow()
        .map { it.addDefaultsIfNecessary() }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    val userHighlights = userHighlightDao.getUserHighlightsFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    val badgeHighlights = badgeHighlightDao.getBadgeHighlightsFlow()
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    val blacklistedUsers = blacklistedUserDao.getBlacklistedUserFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    private val validMessageHighlights = messageHighlights
        .map { highlights -> highlights.filter { it.enabled && (it.type != MessageHighlightEntityType.Custom || it.pattern.isNotBlank()) } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val validUserHighlights = userHighlights
        .map { highlights -> highlights.filter { it.enabled && it.username.isNotBlank() } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val validBadgeHighlights = badgeHighlights
        .map { highlights -> highlights.filter { it.enabled && it.badgeName.isNotBlank() } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val validBlacklistedUsers = blacklistedUsers
        .map { highlights -> highlights.filter { it.enabled && it.username.isNotBlank() } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    suspend fun calculateHighlightState(message: Message): Message {
        return when (message) {
            is UserNoticeMessage      -> message.calculateHighlightState()
            is PointRedemptionMessage -> message.calculateHighlightState()
            is PrivMessage            -> message.calculateHighlightState()
            is WhisperMessage         -> message.calculateHighlightState()
            else                      -> message
        }
    }

    fun runMigrationsIfNeeded() = coroutineScope.launch {
        runCatching {
            if (messageHighlightDao.getMessageHighlights().isEmpty()) {
                Log.d(TAG, "Running message highlights migration...")
                messageHighlightDao.addHighlights(DEFAULT_MESSAGE_HIGHLIGHTS)
                val totalMessageHighlights = DEFAULT_MESSAGE_HIGHLIGHTS.size
                Log.d(TAG, "Message highlights migration completed, added $totalMessageHighlights entries.")
            }
            if (badgeHighlightDao.getBadgeHighlights().isEmpty()) {
                Log.d(TAG, "Running badge highlights migration...")
                badgeHighlightDao.addHighlights(DEFAULT_BADGE_HIGHLIGHTS)
                val totalBadgeHighlights =  + DEFAULT_BADGE_HIGHLIGHTS.size
                Log.d(TAG, "Badge highlights migration completed, added $totalBadgeHighlights entries.")
            }
        }.getOrElse {
            Log.e(TAG, "Failed to run highlights migration", it)
            runCatching {
                messageHighlightDao.deleteAllHighlights()
                userHighlightDao.deleteAllHighlights()
                badgeHighlightDao.deleteAllHighlights()
                return@launch
            }
        }
    }

    suspend fun addMessageHighlight(): MessageHighlightEntity {
        val entity = MessageHighlightEntity(
            id = 0,
            enabled = true,
            type = MessageHighlightEntityType.Custom,
            pattern = ""
        )
        val id = messageHighlightDao.addHighlight(entity)
        return entity.copy(id = id)
    }

    suspend fun updateMessageHighlight(entity: MessageHighlightEntity) {
        messageHighlightDao.addHighlight(entity)
    }

    suspend fun removeMessageHighlight(entity: MessageHighlightEntity) {
        messageHighlightDao.deleteHighlight(entity)
    }

    suspend fun updateMessageHighlights(entities: List<MessageHighlightEntity>) {
        messageHighlightDao.addHighlights(entities)
    }

    suspend fun addUserHighlight(): UserHighlightEntity {
        val entity = UserHighlightEntity(
            id = 0,
            enabled = true,
            username = ""
        )
        val id = userHighlightDao.addHighlight(entity)
        return entity.copy(id = id)
    }

    suspend fun updateUserHighlight(entity: UserHighlightEntity) {
        userHighlightDao.addHighlight(entity)
    }

    suspend fun removeUserHighlight(entity: UserHighlightEntity) {
        userHighlightDao.deleteHighlight(entity)
    }

    suspend fun updateUserHighlights(entities: List<UserHighlightEntity>) {
        userHighlightDao.addHighlights(entities)
    }

    suspend fun addBadgeHighlight(): BadgeHighlightEntity {
        val entity = BadgeHighlightEntity(
            id = 0,
            enabled = true,
            badgeName = "",
            isCustom = true,
        )
        val id = badgeHighlightDao.addHighlight(entity)
        return entity.copy(id = id)
    }

    suspend fun updateBadgeHighlight(entity: BadgeHighlightEntity) {
        badgeHighlightDao.addHighlight(entity)
    }

    suspend fun removeBadgeHighlight(entity: BadgeHighlightEntity) {
        badgeHighlightDao.deleteHighlight(entity)
    }

    suspend fun updateBadgeHighlights(entities: List<BadgeHighlightEntity>) {
        badgeHighlightDao.addHighlights(entities)
    }

    suspend fun addBlacklistedUser(): BlacklistedUserEntity {
        val entity = BlacklistedUserEntity(
            id = 0,
            enabled = true,
            username = ""
        )
        val id = blacklistedUserDao.addBlacklistedUser(entity)
        return entity.copy(id = id)
    }

    suspend fun updateBlacklistedUser(entity: BlacklistedUserEntity) {
        blacklistedUserDao.addBlacklistedUser(entity)
    }

    suspend fun removeBlacklistedUser(entity: BlacklistedUserEntity) {
        blacklistedUserDao.deleteBlacklistedUser(entity)
    }

    suspend fun updateBlacklistedUser(entities: List<BlacklistedUserEntity>) {
        blacklistedUserDao.addBlacklistedUsers(entities)
    }

    private fun UserNoticeMessage.calculateHighlightState(): UserNoticeMessage {
        val messageHighlights = validMessageHighlights.value

        val highlights = buildSet {
            val subsHighlight = messageHighlights.subsHighlight
            if (isSub && subsHighlight != null) {
                add(Highlight(HighlightType.Subscription, subsHighlight.customColor))
            }

            val announcementsHighlight = messageHighlights.announcementsHighlight
            if (isAnnouncement && announcementsHighlight != null) {
                add(Highlight(HighlightType.Announcement, announcementsHighlight.customColor))
            }
        }

        return copy(
            highlights = highlights,
            childMessage = childMessage?.calculateHighlightState()
        )
    }

    private fun PointRedemptionMessage.calculateHighlightState(): PointRedemptionMessage {
        val rewardsHighlight = validMessageHighlights.value.rewardsHighlight
        if (rewardsHighlight != null) {
            return copy(highlights = setOf(Highlight(HighlightType.ChannelPointRedemption, rewardsHighlight.customColor)))
        }
        return copy(highlights = emptySet())
    }

    private fun PrivMessage.calculateHighlightState(): PrivMessage {
        // Disable highlights for shared chat messages to avoid duplicate pings
        if (sourceChannel != null) {
            return this
        }

        if (isUserBlacklisted(name)) {
            return this
        }

        val userHighlights = validUserHighlights.value
        val badgeHighlights = validBadgeHighlights.value
        val messageHighlights = validMessageHighlights.value
        val highlights = buildSet {
            val subsHighlight = messageHighlights.subsHighlight
            if (isSub && subsHighlight != null) {
                add(Highlight(HighlightType.Subscription, subsHighlight.customColor))
            }

            val announcementsHighlight = messageHighlights.announcementsHighlight
            if (isAnnouncement && announcementsHighlight != null) {
                add(Highlight(HighlightType.Announcement, announcementsHighlight.customColor))
            }

            val rewardsHighlight = messageHighlights.rewardsHighlight
            if (isReward && rewardsHighlight != null) {
                add(Highlight(HighlightType.ChannelPointRedemption, rewardsHighlight.customColor))
            }

            val firstMessageHighlight = messageHighlights.firstMessageHighlight
            if (isFirstMessage && firstMessageHighlight != null) {
                add(Highlight(HighlightType.FirstMessage, firstMessageHighlight.customColor))
            }

            val elevatedMessageHighlight = messageHighlights.elevatedMessageHighlight
            if (isElevatedMessage && elevatedMessageHighlight != null) {
                add(Highlight(HighlightType.ElevatedMessage, elevatedMessageHighlight.customColor))
            }

            if (containsCurrentUserName) {
                val highlight = messageHighlights.userNameHighlight
                if (highlight?.enabled == true) {
                    add(Highlight(HighlightType.Username, highlight.customColor))
                    addNotificationHighlightIfEnabled(highlight)
                }
            }

            if (containsParticipatedReply) {
                val highlight = messageHighlights.repliesHighlight
                if (highlight?.enabled == true) {
                    add(Highlight(HighlightType.Reply, highlight.customColor))
                    addNotificationHighlightIfEnabled(highlight)
                }
            }

            messageHighlights
                .filter { it.type == MessageHighlightEntityType.Custom }
                .forEach {
                    val regex = it.regex ?: return@forEach

                    if (message.contains(regex)) {
                        add(Highlight(HighlightType.Custom, it.customColor))
                        addNotificationHighlightIfEnabled(it)
                    }
                }

            userHighlights.forEach {
                if (name.matches(it.username)) {
                    add(Highlight(HighlightType.Custom, it.customColor))
                    addNotificationHighlightIfEnabled(it)
                }
            }
            badgeHighlights.forEach { highlight ->
                badges.forEach { badge ->
                    val tag = badge.badgeTag ?: return@forEach
                    if (tag.isNotBlank()) {
                        val match = if (highlight.badgeName.contains("/")) {
                            tag == highlight.badgeName
                        } else {
                            tag.startsWith(highlight.badgeName + "/")
                        }
                        if (match) {
                            add(Highlight(HighlightType.Badge, highlight.customColor))
                            addNotificationHighlightIfEnabled(highlight)
                        }
                    }
                }
            }
        }

        return copy(highlights = highlights)
    }

    private suspend fun WhisperMessage.calculateHighlightState(): WhisperMessage = when {
        notificationsSettingsDataStore.settings.first().showWhisperNotifications -> copy(highlights = setOf(Highlight(HighlightType.Notification)))
        else                                                              -> this
    }

    private val List<MessageHighlightEntity>.subsHighlight: MessageHighlightEntity?
        get() = find { it.type == MessageHighlightEntityType.Subscription }

    private val List<MessageHighlightEntity>.announcementsHighlight : MessageHighlightEntity?
        get() = find { it.type == MessageHighlightEntityType.Announcement }

    private val List<MessageHighlightEntity>.rewardsHighlight: MessageHighlightEntity?
        get() = find { it.type == MessageHighlightEntityType.ChannelPointRedemption }

    private val List<MessageHighlightEntity>.firstMessageHighlight: MessageHighlightEntity?
        get() = find { it.type == MessageHighlightEntityType.FirstMessage }

    private val List<MessageHighlightEntity>.elevatedMessageHighlight: MessageHighlightEntity?
        get() = find { it.type == MessageHighlightEntityType.ElevatedMessage }

    private val List<MessageHighlightEntity>.repliesHighlight: MessageHighlightEntity?
        get() = find { it.type == MessageHighlightEntityType.Reply }

    private val List<MessageHighlightEntity>.userNameHighlight: MessageHighlightEntity?
        get() = find { it.type == MessageHighlightEntityType.Username }

    private fun MutableCollection<Highlight>.addNotificationHighlightIfEnabled(highlightEntity: MessageHighlightEntity) {
        if (highlightEntity.createNotification) {
            add(Highlight(HighlightType.Notification))
        }
    }

    private fun MutableCollection<Highlight>.addNotificationHighlightIfEnabled(highlightEntity: UserHighlightEntity) {
        if (highlightEntity.createNotification) {
            add(Highlight(HighlightType.Notification))
        }
    }

    private fun MutableCollection<Highlight>.addNotificationHighlightIfEnabled(highlightEntity: BadgeHighlightEntity) {
        if (highlightEntity.createNotification) {
            add(Highlight(HighlightType.Notification))
        }
    }

    private val PrivMessage.containsCurrentUserName: Boolean
        get() {
            val currentUser = currentUserAndDisplay.value?.first ?: return false
            if (name.matches(currentUser)) {
                return false
            }

            val regex = currentUserRegex.value ?: return false
            return message.contains(regex)
        }

    private val PrivMessage.containsParticipatedReply: Boolean
        get() = thread?.participated == true && name != currentUserAndDisplay.value?.first

    private fun createUserAndDisplayRegex(values: Pair<UserName?, DisplayName?>?): Regex? {
        val (user, display) = values ?: return null
        user ?: return null
        val displayRegex = display
            ?.takeIf { !user.matches(it) }
            ?.let { "|$it" }.orEmpty()
        return """\b$user$displayRegex\b""".toRegex(RegexOption.IGNORE_CASE)
    }

    private fun isUserBlacklisted(name: UserName): Boolean {
        validBlacklistedUsers.value
            .forEach {
                val hasMatch = when {
                    it.isRegex -> it.regex?.let { regex -> name.matches(regex) } ?: false
                    else       -> name.matches(it.username)
                }

                if (hasMatch) {
                    return true
                }
            }

        return false
    }

    private fun List<MessageHighlightEntity>.addDefaultsIfNecessary(): List<MessageHighlightEntity> {
        return (this + DEFAULT_MESSAGE_HIGHLIGHTS).distinctBy {
            when (it.type) {
                MessageHighlightEntityType.Custom -> it.id
                else                              -> it.type
            }
        }.sortedBy { it.type.ordinal }
    }

    companion object {
        private val TAG = HighlightsRepository::class.java.simpleName
        private val DEFAULT_MESSAGE_HIGHLIGHTS = listOf(
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.Username, pattern = ""),
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.Subscription, pattern = "", createNotification = false),
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.Announcement, pattern = "", createNotification = false),
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.ChannelPointRedemption, pattern = "", createNotification = false),
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.FirstMessage, pattern = "", createNotification = false),
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.ElevatedMessage, pattern = "", createNotification = false),
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.Reply, pattern = ""),
        )
        private val DEFAULT_BADGE_HIGHLIGHTS = listOf(
            BadgeHighlightEntity(id = 0, enabled = false, badgeName = "broadcaster", isCustom = false, customColor = 0x7f7f3f49.toInt()),
            BadgeHighlightEntity(id = 0, enabled = false, badgeName = "admin", isCustom = false, customColor = 0x7f8f3018.toInt()),
            BadgeHighlightEntity(id = 0, enabled = false, badgeName = "staff", isCustom = false, customColor = 0x7f8f3018.toInt()),
            BadgeHighlightEntity(id = 0, enabled = false, badgeName = "moderator", isCustom = false, customColor = 0x731f8d2b.toInt()),
            BadgeHighlightEntity(id = 0, enabled = false, badgeName = "lead_moderator", isCustom = false, customColor = 0x731f8d2b.toInt()),
            BadgeHighlightEntity(id = 0, enabled = false, badgeName = "partner", isCustom = false, customColor = 0x64c466ff.toInt()),
            BadgeHighlightEntity(id = 0, enabled = false, badgeName = "vip", isCustom = false, customColor = 0x7fc12ea9.toInt()),
            BadgeHighlightEntity(id = 0, enabled = false, badgeName = "founder", isCustom = false),
            BadgeHighlightEntity(id = 0, enabled = false, badgeName = "subscriber", isCustom = false),
        )
    }
}
