package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.database.dao.MessageIgnoreDao
import com.flxrs.dankchat.data.database.dao.UserIgnoreDao
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntity
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntityType
import com.flxrs.dankchat.data.database.entity.UserIgnoreEntity
import com.flxrs.dankchat.data.twitch.message.EmoteWithPositions
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.PointRedemptionMessage
import com.flxrs.dankchat.data.twitch.message.PositionedTextEdit
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.data.twitch.message.applyTextEdits
import com.flxrs.dankchat.data.twitch.message.isAnnouncement
import com.flxrs.dankchat.data.twitch.message.isElevatedMessage
import com.flxrs.dankchat.data.twitch.message.isFirstMessage
import com.flxrs.dankchat.data.twitch.message.isMilestone
import com.flxrs.dankchat.data.twitch.message.isReward
import com.flxrs.dankchat.data.twitch.message.isSub
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

private val logger = KotlinLogging.logger("IgnoresRepository")

@Single
class IgnoresRepository(
    private val helixApiClient: HelixApiClient,
    private val messageIgnoreDao: MessageIgnoreDao,
    private val userIgnoreDao: UserIgnoreDao,
    private val preferences: DankChatPreferenceStore,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)

    data class TwitchBlock(
        val id: UserId,
        val name: UserName,
    )

    private val _twitchBlocks = MutableStateFlow(emptySet<TwitchBlock>())

    val messageIgnores =
        messageIgnoreDao
            .getMessageIgnoresFlow()
            .map { ignores -> ignores.sortedBy { it.type.ordinal } }
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    val userIgnores = userIgnoreDao.getUserIgnoresFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    val twitchBlocks = _twitchBlocks.asStateFlow()

    private val validMessageIgnores =
        messageIgnores
            .map { ignores -> ignores.filter { it.enabled && (it.type != MessageIgnoreEntityType.Custom || it.pattern.isNotBlank()) } }
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val customMessageIgnores =
        validMessageIgnores
            .map { ignores -> ignores.filter { it.type == MessageIgnoreEntityType.Custom } }
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val validUserIgnores =
        userIgnores
            .map { ignores -> ignores.filter { it.enabled && it.username.isNotBlank() } }
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    fun applyIgnores(message: Message): Message? = when (message) {
        is PointRedemptionMessage -> message.applyIgnores()
        is PrivMessage -> message.applyIgnores()
        is UserNoticeMessage -> message.applyIgnores()
        is WhisperMessage -> message.applyIgnores()
        else -> message
    }

    fun runMigrationsIfNeeded() = coroutineScope.launch {
        runCatching {
            val existingIgnores = messageIgnoreDao.getMessageIgnores()

            // Non-custom types must exist exactly once; keep the oldest row and drop the rest
            val duplicates = existingIgnores
                .filter { it.type != MessageIgnoreEntityType.Custom }
                .groupBy { it.type }
                .values
                .flatMap { it.drop(1) }
            duplicates.forEach { messageIgnoreDao.deleteIgnore(it) }

            val existingTypes = existingIgnores.mapTo(mutableSetOf()) { it.type }
            val missingDefaults = DEFAULT_IGNORES.filter { it.type !in existingTypes }
            if (missingDefaults.isNotEmpty()) {
                logger.debug { "Running ignores migration..." }
                messageIgnoreDao.addIgnores(missingDefaults)
                logger.debug { "Ignores migration completed, added ${missingDefaults.size} entries." }
            }
        }.getOrElse {
            logger.error(it) { "Failed to run ignores migration" }
            runCatching {
                messageIgnoreDao.deleteAllIgnores()
                userIgnoreDao.deleteAllIgnores()
                return@launch
            }
        }
    }

    fun isUserBlocked(userId: UserId?): Boolean = _twitchBlocks.value.any { it.id == userId }

    suspend fun loadUserBlocks() = withContext(dispatchersProvider.default) {
        if (!preferences.isLoggedIn) {
            return@withContext
        }

        val userId = preferences.userIdString ?: return@withContext
        // Uses unvalidated variant so blocks load during startup before validation
        // resolves. A stale token just 401s and is swallowed below.
        val blocks = helixApiClient.getUserBlocksUnvalidated(userId).getOrElse {
            logger.debug(it) { "Failed to load user blocks for $userId" }
            return@withContext
        }
        val twitchBlocks =
            blocks.mapTo(mutableSetOf()) { block ->
                TwitchBlock(
                    id = block.id,
                    name = block.name,
                )
            }

        _twitchBlocks.update { twitchBlocks }
    }

    suspend fun addUserBlock(
        targetUserId: UserId,
        targetUsername: UserName,
    ) {
        val result = helixApiClient.blockUser(targetUserId)
        if (result.isSuccess) {
            _twitchBlocks.update {
                it +
                    TwitchBlock(
                        id = targetUserId,
                        name = targetUsername,
                    )
            }
        }
    }

    suspend fun removeUserBlock(
        targetUserId: UserId,
        targetUsername: UserName,
    ) {
        val result = helixApiClient.unblockUser(targetUserId)
        if (result.isSuccess) {
            _twitchBlocks.update {
                it -
                    TwitchBlock(
                        id = targetUserId,
                        name = targetUsername,
                    )
            }
        }
    }

    fun clearIgnores() = _twitchBlocks.update { emptySet() }

    suspend fun addMessageIgnore(): MessageIgnoreEntity {
        val entity =
            MessageIgnoreEntity(
                id = 0,
                enabled = true,
                type = MessageIgnoreEntityType.Custom,
                pattern = "",
                isBlockMessage = false,
                replacement = "***",
            )
        val id = messageIgnoreDao.addIgnore(entity)
        return entity.copy(id = id)
    }

    suspend fun updateMessageIgnore(entity: MessageIgnoreEntity) {
        messageIgnoreDao.addIgnore(entity)
    }

    suspend fun removeMessageIgnore(entity: MessageIgnoreEntity) {
        messageIgnoreDao.deleteIgnore(entity)
    }

    suspend fun updateMessageIgnores(entities: List<MessageIgnoreEntity>) {
        messageIgnoreDao.addIgnores(entities)
    }

    suspend fun addUserIgnore(): UserIgnoreEntity {
        val entity =
            UserIgnoreEntity(
                id = 0,
                enabled = true,
                username = "",
            )
        val id = userIgnoreDao.addIgnore(entity)
        return entity.copy(id = id)
    }

    suspend fun updateUserIgnore(entity: UserIgnoreEntity) {
        userIgnoreDao.addIgnore(entity)
    }

    suspend fun removeUserIgnore(entity: UserIgnoreEntity) {
        userIgnoreDao.deleteIgnore(entity)
    }

    suspend fun updateUserIgnores(entities: List<UserIgnoreEntity>) {
        userIgnoreDao.addIgnores(entities)
    }

    private fun UserNoticeMessage.applyIgnores(): UserNoticeMessage? {
        val messageIgnores = validMessageIgnores.value

        if (isSub && messageIgnores.isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.Subscription)) {
            return null
        }

        if (isAnnouncement && messageIgnores.isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.Announcement)) {
            return null
        }

        if (isMilestone && messageIgnores.isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.WatchStreak)) {
            return null
        }

        return copy(
            childMessage = childMessage?.applyIgnores(),
        )
    }

    @Suppress("ReturnCount")
    private fun PrivMessage.applyIgnores(): PrivMessage? {
        val messageIgnores = validMessageIgnores.value

        if (isSub && messageIgnores.isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.Subscription)) {
            return null
        }

        if (isAnnouncement && messageIgnores.isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.Announcement)) {
            return null
        }

        if (isReward && messageIgnores.isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.ChannelPointRedemption)) {
            return null
        }

        if (isElevatedMessage && messageIgnores.isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.ElevatedMessage)) {
            return null
        }

        if (isFirstMessage && messageIgnores.isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.FirstMessage)) {
            return null
        }

        if (isIgnoredUsername(name)) {
            return null
        }

        customMessageIgnores.value
            .isIgnoredMessageWithReplacement(message) { replacement ->
                replacement ?: return null
                val filteredPositions = adaptEmotePositions(replacement, emoteData.emotesWithPositions)
                val adjustedGifs = gifData.gifs.applyTextEdits(replacement.toTextEdits())
                return copy(
                    message = replacement.filtered,
                    originalMessage = replacement.filtered,
                    gifs = adjustedGifs,
                    gifData = gifData.copy(message = replacement.filtered, gifs = adjustedGifs),
                    emoteData = emoteData.copy(message = replacement.filtered, emotesWithPositions = filteredPositions),
                )
            }

        return this
    }

    private fun PointRedemptionMessage.applyIgnores(): PointRedemptionMessage? {
        val redemptionsIgnored =
            validMessageIgnores.value
                .any { it.type == MessageIgnoreEntityType.ChannelPointRedemption }

        if (redemptionsIgnored) {
            return null
        }

        return this
    }

    private fun WhisperMessage.applyIgnores(): WhisperMessage? {
        if (isIgnoredUsername(name)) {
            return null
        }

        customMessageIgnores.value
            .isIgnoredMessageWithReplacement(message) { replacement ->
                replacement ?: return null
                val filteredPositions = adaptEmotePositions(replacement, emoteData.emotesWithPositions)
                return copy(
                    message = replacement.filtered,
                    originalMessage = replacement.filtered,
                    emoteData = emoteData.copy(message = replacement.filtered, emotesWithPositions = filteredPositions),
                )
            }

        return this
    }

    private fun List<MessageIgnoreEntity>.isMessageIgnoreTypeEnabled(type: MessageIgnoreEntityType): Boolean = any { it.type == type }

    private fun isIgnoredUsername(name: UserName): Boolean {
        validUserIgnores.value
            .forEach {
                val hasMatch =
                    when {
                        it.isRegex -> it.regex?.let { regex -> name.value.matches(regex) } ?: false
                        else -> name.matches(it.username, ignoreCase = !it.isCaseSensitive)
                    }

                if (hasMatch) {
                    return true
                }
            }

        return false
    }

    private data class ReplacementResult(
        val filtered: String,
        val replacementLength: Int,
        val matchedRanges: List<IntRange>,
    )

    private inline fun List<MessageIgnoreEntity>.isIgnoredMessageWithReplacement(
        message: String,
        onReplacement: (ReplacementResult?) -> Unit,
    ) {
        forEach { ignoreEntity ->
            val regex = ignoreEntity.regex ?: return@forEach
            val results = regex.findAll(message).toList()

            if (results.isNotEmpty()) {
                ignoreEntity.escapedReplacement?.let { escapedReplacement ->
                    val filtered = message.replace(regex, escapedReplacement)
                    return onReplacement(
                        ReplacementResult(
                            filtered = filtered,
                            replacementLength = ignoreEntity.replacement.orEmpty().length,
                            matchedRanges = results.map(MatchResult::range),
                        ),
                    )
                }

                return onReplacement(null)
            }
        }
    }

    private fun adaptEmotePositions(
        replacement: ReplacementResult,
        emotes: List<EmoteWithPositions>,
    ): List<EmoteWithPositions> = emotes.map { emoteWithPos ->
        val adjusted =
            emoteWithPos.positions
                .filterNot { pos -> replacement.matchedRanges.any { match -> match in pos || pos in match } } // filter out emotes directly affected by ignore replacement
                .map { pos ->
                    val offset =
                        replacement.matchedRanges
                            .filter { it.last < pos.first } // only replacements before an emote need to be considered
                            .sumOf { replacement.replacementLength - (it.last + 1 - it.first) } // change between original match and replacement
                    pos.first + offset..pos.last + offset // add sum of changes to the emote position
                }
        emoteWithPos.copy(positions = adjusted)
    }

    private fun ReplacementResult.toTextEdits(): List<PositionedTextEdit> = matchedRanges.map { range ->
        PositionedTextEdit(
            start = range.first,
            endExclusive = range.last + 1,
            replacementLength = replacementLength,
        )
    }

    private operator fun IntRange.contains(other: IntRange): Boolean = other.first >= first && other.last <= last

    companion object {
        private val DEFAULT_IGNORES =
            listOf(
                MessageIgnoreEntity(id = 0, enabled = false, type = MessageIgnoreEntityType.Subscription, pattern = ""),
                MessageIgnoreEntity(id = 0, enabled = false, type = MessageIgnoreEntityType.Announcement, pattern = ""),
                MessageIgnoreEntity(id = 0, enabled = false, type = MessageIgnoreEntityType.WatchStreak, pattern = ""),
                MessageIgnoreEntity(id = 0, enabled = false, type = MessageIgnoreEntityType.ChannelPointRedemption, pattern = ""),
                MessageIgnoreEntity(id = 0, enabled = false, type = MessageIgnoreEntityType.FirstMessage, pattern = ""),
                MessageIgnoreEntity(id = 0, enabled = false, type = MessageIgnoreEntityType.ElevatedMessage, pattern = ""),
            )
    }
}
