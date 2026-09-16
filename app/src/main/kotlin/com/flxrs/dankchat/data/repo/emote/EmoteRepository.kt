package com.flxrs.dankchat.data.repo.emote

import android.graphics.Color
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.toColorInt
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.bttv.dto.BTTVChannelDto
import com.flxrs.dankchat.data.api.bttv.dto.BTTVEmoteDto
import com.flxrs.dankchat.data.api.bttv.dto.BTTVGlobalEmoteDto
import com.flxrs.dankchat.data.api.dankchat.dto.DankChatBadgeDto
import com.flxrs.dankchat.data.api.ffz.dto.FFZChannelDto
import com.flxrs.dankchat.data.api.ffz.dto.FFZEmoteDto
import com.flxrs.dankchat.data.api.ffz.dto.FFZGlobalDto
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.dto.ChannelEmoteDto
import com.flxrs.dankchat.data.api.helix.dto.CheermoteSetDto
import com.flxrs.dankchat.data.api.helix.dto.UserEmoteDto
import com.flxrs.dankchat.data.api.seventv.SevenTVUserDetails
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteFileDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteSetDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVUserConnection
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVUserDto
import com.flxrs.dankchat.data.api.seventv.eventapi.SevenTVEventMessage
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.badge.BadgeSet
import com.flxrs.dankchat.data.twitch.badge.BadgeType
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmoteType
import com.flxrs.dankchat.data.twitch.emote.CheermoteSet
import com.flxrs.dankchat.data.twitch.emote.CheermoteTier
import com.flxrs.dankchat.data.twitch.emote.EmoteType
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.data.twitch.emote.toChatMessageEmoteType
import com.flxrs.dankchat.data.twitch.message.EmoteWithPositions
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.PositionedTextEdit
import com.flxrs.dankchat.data.twitch.message.PositionedTextEditOverlapPolicy
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.data.twitch.message.applyTextEdits
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.utils.extensions.analyzeCodePoints
import com.flxrs.dankchat.utils.extensions.appendSpacesBetweenEmojiGroup
import com.flxrs.dankchat.utils.extensions.codePointAsString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger("EmoteRepository")

@Single
class EmoteRepository(
    private val helixApiClient: HelixApiClient,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val channelRepository: ChannelRepository,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val ffzModBadges = ConcurrentHashMap<UserName, String>()
    private val ffzVipBadges = ConcurrentHashMap<UserName, String>()
    private val channelBadges = ConcurrentHashMap<UserName, Map<String, BadgeSet>>()
    private val globalBadges = ConcurrentHashMap<String, BadgeSet>()
    private val dankChatBadges = CopyOnWriteArrayList<DankChatBadgeDto>()

    private val sevenTvChannelDetails = ConcurrentHashMap<UserName, SevenTVUserDetails>()

    private val globalEmoteState = MutableStateFlow(GlobalEmoteState())
    private val channelEmoteStates = ConcurrentHashMap<UserName, MutableStateFlow<ChannelEmoteState>>()

    /**
     * Per-channel cache of the merged 3rd-party emote lookup map (without Twitch emotes).
     * Invalidated via referential identity checks on the global/channel state snapshots.
     */
    private val cachedEmoteMaps = ConcurrentHashMap<UserName, CachedEmoteMap>()
    private val cachedMergedEmotes = ConcurrentHashMap<UserName, CachedMergedEmotes>()

    fun getEmotes(channel: UserName): Flow<Emotes> {
        val channelFlow = channelEmoteStates.getOrPut(channel) { MutableStateFlow(ChannelEmoteState()) }
        return combine(globalEmoteState, channelFlow) { globalState, channelState ->
            getOrMergeEmotes(channel, globalState, channelState)
        }
    }

    // Merging eagerly sorts all emotes, new collectors must reuse the result for unchanged state
    private fun getOrMergeEmotes(
        channel: UserName,
        globalState: GlobalEmoteState,
        channelState: ChannelEmoteState,
    ): Emotes {
        val cached = cachedMergedEmotes[channel]
        if (cached != null && cached.globalState === globalState && cached.channelState === channelState) {
            return cached.emotes
        }

        val merged = mergeEmotes(globalState, channelState)
        cachedMergedEmotes[channel] = CachedMergedEmotes(globalState, channelState, merged)
        return merged
    }

    fun createFlowsIfNecessary(channels: List<UserName>) {
        channels.forEach { channelEmoteStates.putIfAbsent(it, MutableStateFlow(ChannelEmoteState())) }
    }

    fun removeChannel(channel: UserName) {
        channelEmoteStates.remove(channel)
        cachedEmoteMaps.remove(channel)
        cachedMergedEmotes.remove(channel)
    }

    fun clearTwitchEmotes() {
        globalEmoteState.update { it.copy(twitchEmotes = emptyList()) }
        channelEmoteStates.values.forEach { state ->
            state.update { it.copy(twitchEmotes = emptyList()) }
        }
    }

    fun parse3rdPartyEmotes(
        message: String,
        channel: UserName,
        withTwitch: Boolean = false,
    ): List<ChatMessageEmote> {
        val emoteMap = getOrBuildEmoteMap(channel, withTwitch)

        // Single pass through words
        return buildList {
            message.forEachWord { word, startIndex ->
                emoteMap[word]?.let { emote ->
                    this +=
                        ChatMessageEmote(
                            position = startIndex..startIndex + word.length,
                            url = emote.url,
                            id = emote.id,
                            code = emote.code,
                            scale = emote.scale,
                            type = emote.emoteType.toChatMessageEmoteType(),
                            isOverlayEmote = emote.isOverlayEmote,
                        )
                }
            }
        }
    }

    fun findEmoteIdsInMessage(
        message: String,
        channel: UserName,
    ): Set<String> {
        val emoteMap = getOrBuildEmoteMap(channel, withTwitch = true)
        return buildSet {
            message.forEachWord { word, _ ->
                emoteMap[word]?.let { add(it.id) }
            }
        }
    }

    private fun getOrBuildEmoteMap(
        channel: UserName,
        withTwitch: Boolean,
    ): Map<String, GenericEmote> {
        val globalState = globalEmoteState.value
        val channelState = channelEmoteStates[channel]?.value ?: ChannelEmoteState()

        // Use cached map for the hot path (without Twitch emotes)
        if (!withTwitch) {
            val cached = cachedEmoteMaps[channel]
            if (cached != null && cached.globalState === globalState && cached.channelState === channelState) {
                return cached.map
            }
        }

        val isWhisper = channel == WhisperMessage.WHISPER_CHANNEL

        // Build lookup map: lowest priority first, highest last (last write wins)
        // Priority: Twitch > Channel FFZ > Channel BTTV > Channel 7TV > Global FFZ > Global BTTV > Global 7TV
        val map = HashMap<String, GenericEmote>()
        globalState.sevenTvEmotes.associateByTo(map) { it.code }
        globalState.bttvEmotes.associateByTo(map) { it.code }
        globalState.ffzEmotes.associateByTo(map) { it.code }
        if (!isWhisper) {
            channelState.sevenTvEmotes.associateByTo(map) { it.code }
            channelState.bttvEmotes.associateByTo(map) { it.code }
            channelState.ffzEmotes.associateByTo(map) { it.code }
        }
        if (withTwitch) {
            globalState.twitchEmotes.associateByTo(map) { it.code }
            channelState.twitchEmotes.associateByTo(map) { it.code }
        }

        if (!withTwitch) {
            cachedEmoteMaps[channel] = CachedEmoteMap(globalState, channelState, map)
        }

        return map
    }

    suspend fun parseEmotesAndBadges(message: Message): Message {
        val replyMentionOffset = (message as? PrivMessage)?.replyMentionOffset ?: 0
        val emoteData = message.emoteData ?: return message
        val (messageString, channel, emotesWithPositions) = emoteData

        val withEmojiFix =
            messageString.replace(
                ESCAPE_TAG_REGEX,
                ZERO_WIDTH_JOINER,
            )
        var adjustedGifs = (message as? PrivMessage)?.gifData?.gifs.orEmpty()
        if (adjustedGifs.isNotEmpty()) {
            adjustedGifs = adjustedGifs.applyTextEdits(
                ESCAPE_TAG_REGEX
                    .findAll(messageString)
                    .map { match ->
                        PositionedTextEdit(match.range.first, match.range.last + 1, ZERO_WIDTH_JOINER.length)
                    }.toList(),
                PositionedTextEditOverlapPolicy.PreserveContainedEdits,
            )
        }

        // Combined single-pass: find supplementary codepoint positions AND remove duplicate whitespace
        val (supplementaryCodePointPositions, duplicateSpaceAdjustedMessage, removedSpaces) = withEmojiFix.analyzeCodePoints()
        if (adjustedGifs.isNotEmpty()) {
            adjustedGifs = adjustedGifs.applyTextEdits(
                duplicateWhitespaceEdits(withEmojiFix),
                PositionedTextEditOverlapPolicy.PreserveContainedEdits,
            )
        }
        val (appendedSpaceAdjustedMessage, appendedSpaces) = duplicateSpaceAdjustedMessage.appendSpacesBetweenEmojiGroup()
        if (adjustedGifs.isNotEmpty()) {
            adjustedGifs = adjustedGifs.applyTextEdits(
                appendedSpaces.map { position -> PositionedTextEdit(position, position, 1) },
                PositionedTextEditOverlapPolicy.PreserveContainedEdits,
            )
        }

        val twitchEmotes =
            parseTwitchEmotes(
                emotesWithPositions = emotesWithPositions,
                message = appendedSpaceAdjustedMessage,
                supplementaryCodePointPositions = supplementaryCodePointPositions,
                appendedSpaces = appendedSpaces,
                removedSpaces = removedSpaces,
                replyMentionOffset = replyMentionOffset,
            )
        val twitchEmoteCodes = twitchEmotes.mapTo(mutableSetOf()) { it.code }
        val hasBits = message is PrivMessage && message.tags["bits"] != null
        val (thirdPartyEmotes, cheermotes) = parseNonTwitchEmotes(
            message = appendedSpaceAdjustedMessage,
            channel = channel,
            excludeCodes = twitchEmoteCodes,
            hasBits = hasBits,
        )
        val emotes = twitchEmotes + thirdPartyEmotes + cheermotes

        val overlayResult = adjustOverlayEmotes(appendedSpaceAdjustedMessage, emotes, trackTextEdits = adjustedGifs.isNotEmpty())
        val (adjustedMessage, adjustedEmotes) = overlayResult
        if (adjustedGifs.isNotEmpty()) {
            adjustedGifs =
                adjustedGifs.applyTextEdits(
                    overlayResult.textEdits,
                    PositionedTextEditOverlapPolicy.PreserveContainedEdits,
                )
        }
        val messageWithEmotes =
            when (message) {
                is PrivMessage -> {
                    message.copy(message = adjustedMessage, emotes = adjustedEmotes, gifs = adjustedGifs, originalMessage = withEmojiFix)
                }

                is WhisperMessage -> {
                    message.copy(message = adjustedMessage, emotes = adjustedEmotes, originalMessage = withEmojiFix)
                }

                is UserNoticeMessage -> {
                    message.copy(
                        childMessage =
                            message.childMessage?.copy(
                                message = adjustedMessage,
                                emotes = adjustedEmotes,
                                originalMessage = withEmojiFix,
                            ),
                    )
                }

                else -> {
                    message
                }
            }

        return parseBadges(messageWithEmotes)
    }

    private suspend fun parseBadges(message: Message): Message {
        val badgeData = message.badgeData ?: return message
        val (userId, channel, badgeTag, badgeInfoTag) = badgeData

        val badgeInfos =
            badgeInfoTag
                ?.parseTagList()
                ?.associate { it.key to it.value }
                .orEmpty()

        val badges =
            badgeTag
                ?.parseTagList()
                ?.mapNotNull { (badgeKey, badgeValue, tag) ->
                    val badgeInfo = badgeInfos[badgeKey]

                    val globalBadgeUrl = getGlobalBadgeUrl(badgeKey, badgeValue)
                    val channelBadgeUrl = getChannelBadgeUrl(channel, badgeKey, badgeValue)
                    val ffzModBadgeUrl = getFfzModBadgeUrl(channel)
                    val ffzVipBadgeUrl = getFfzVipBadgeUrl(channel)

                    val title = getBadgeTitle(channel, badgeKey, badgeValue)
                    val type = BadgeType.parseFromBadgeId(badgeKey)
                    when {
                        badgeKey.startsWith("moderator") && ffzModBadgeUrl != null -> {
                            Badge.FFZModBadge(
                                title = title,
                                badgeTag = tag,
                                badgeInfo = badgeInfo,
                                url = ffzModBadgeUrl,
                                type = type,
                            )
                        }

                        badgeKey.startsWith("vip") && ffzVipBadgeUrl != null -> {
                            Badge.FFZVipBadge(
                                title = title,
                                badgeTag = tag,
                                badgeInfo = badgeInfo,
                                url = ffzVipBadgeUrl,
                                type = type,
                            )
                        }

                        // Channel badge sets take precedence over global sets, this also covers
                        // sets that only exist per channel, like campaign badges
                        channelBadgeUrl != null -> {
                            Badge.ChannelBadge(
                                title = title,
                                badgeTag = tag,
                                badgeInfo = badgeInfo,
                                url = channelBadgeUrl,
                                type = type,
                            )
                        }

                        else -> {
                            globalBadgeUrl?.let { Badge.GlobalBadge(title, tag, badgeInfo, it, type) }
                        }
                    }
                }.orEmpty()

        val sharedChatBadge = getSharedChatBadge(message)
        val allBadges =
            buildList {
                if (sharedChatBadge != null) {
                    add(sharedChatBadge)
                }
                addAll(badges)
                val badge = getDankChatBadgeTitleAndUrl(userId)
                if (badge != null) {
                    add(Badge.DankChatBadge(title = badge.first, badgeTag = null, badgeInfo = null, url = badge.second, type = BadgeType.DankChat))
                }
            }

        return when (message) {
            is PrivMessage -> {
                message.copy(badges = allBadges)
            }

            is WhisperMessage -> {
                message.copy(badges = allBadges)
            }

            is UserNoticeMessage -> {
                message.copy(
                    childMessage = message.childMessage?.copy(badges = allBadges),
                )
            }

            else -> {
                message
            }
        }
    }

    private data class CachedEmoteMap(
        val globalState: GlobalEmoteState,
        val channelState: ChannelEmoteState,
        val map: Map<String, GenericEmote>,
    )

    private data class CachedMergedEmotes(
        val globalState: GlobalEmoteState,
        val channelState: ChannelEmoteState,
        val emotes: Emotes,
    )

    data class TagListEntry(
        val key: String,
        val value: String,
        val tag: String,
    )

    private fun String.parseTagList(): List<TagListEntry> = split(',')
        .mapNotNull {
            if (!it.contains('/')) {
                return@mapNotNull null
            }

            val key = it.substringBefore('/')
            val value = it.substringAfter('/')
            TagListEntry(key, value, it)
        }

    private fun getChannelBadgeUrl(
        channel: UserName?,
        set: String,
        version: String,
    ) = channel?.let {
        channelBadges[channel]
            ?.get(set)
            ?.versions
            ?.get(version)
            ?.imageUrlHigh
    }

    private fun getGlobalBadgeUrl(
        set: String,
        version: String,
    ) = globalBadges[set]?.versions?.get(version)?.imageUrlHigh

    private fun getBadgeTitle(
        channel: UserName?,
        set: String,
        version: String,
    ): String? = channel?.let {
        channelBadges[channel]
            ?.get(set)
            ?.versions
            ?.get(version)
            ?.title
    }
        ?: globalBadges[set]?.versions?.get(version)?.title

    private fun getFfzModBadgeUrl(channel: UserName?): String? = channel?.let { ffzModBadges[channel] }

    private fun getFfzVipBadgeUrl(channel: UserName?): String? = channel?.let { ffzVipBadges[channel] }

    private fun getDankChatBadgeTitleAndUrl(userId: UserId?): Pair<String, String>? = dankChatBadges.find { it.users.any { id -> id == userId } }?.let { it.type to it.url }

    private suspend fun getSharedChatBadge(message: Message): Badge? {
        if (message !is PrivMessage) {
            return null
        }

        val sourceRoomId = message.tags["source-room-id"] ?: return null
        val channel = channelRepository.getChannel(sourceRoomId.toUserId())
        if (channel?.avatarUrl == null && sourceRoomId == message.tags["room-id"]) {
            return null // don't show the fallback icon if we don't have the avatar
        }
        return Badge.SharedChatBadge(
            url = channel?.avatarUrl?.replace(oldValue = "300x300", newValue = "70x70").orEmpty(),
            title = "Shared Message${channel?.displayName?.let { " from $it" }.orEmpty()}",
        )
    }

    fun setChannelBadges(
        channel: UserName,
        badges: Map<String, BadgeSet>,
    ) {
        channelBadges[channel] = badges
    }

    fun setGlobalBadges(badges: Map<String, BadgeSet>) {
        globalBadges.putAll(badges)
    }

    fun setDankChatBadges(dto: List<DankChatBadgeDto>) {
        dankChatBadges.clear()
        dankChatBadges.addAll(dto)
    }

    fun getChannelForSevenTVEmoteSet(emoteSetId: String): UserName? = sevenTvChannelDetails
        .entries
        .find { (_, details) -> details.activeEmoteSetId == emoteSetId }
        ?.key

    fun getSevenTVUserDetails(channel: UserName): SevenTVUserDetails? = sevenTvChannelDetails[channel]

    suspend fun loadUserEmotes(
        userId: UserId,
        onFirstPageLoaded: (() -> Unit)? = null,
    ): Result<Unit> = runCatching {
        loadUserEmotesViaHelix(userId, onFirstPageLoaded)
    }

    private suspend fun loadUserEmotesViaHelix(
        userId: UserId,
        onFirstPageLoaded: (() -> Unit)? = null,
    ) = withContext(dispatchersProvider.default) {
        val seenIds = mutableSetOf<String>()
        val allEmotes = mutableListOf<GenericEmote>()
        var totalCount = 0
        var isFirstPage = true

        helixApiClient.getUserEmotesFlow(userId).collect { page ->
            totalCount += page.size

            val newGlobalEmotes = mutableListOf<GenericEmote>()
            val newChannelDtos = mutableListOf<UserEmoteDto>()

            for (emote in page) {
                if (!seenIds.add(emote.id)) continue

                if (emote.emoteType in CHANNEL_EMOTE_TYPES) {
                    newChannelDtos.add(emote)
                } else {
                    newGlobalEmotes.add(emote.toGenericEmote(EmoteType.GlobalTwitchEmote))
                }
            }

            // Resolve channel emotes from this page — getChannelsByIds caches results,
            // so repeated owner IDs across pages are cheap lookups
            if (newChannelDtos.isNotEmpty()) {
                val ownerIds =
                    newChannelDtos
                        .filter { it.ownerId.isNotBlank() }
                        .map { it.ownerId.toUserId() }
                        .distinct()

                val channelsByIdMap =
                    channelRepository
                        .getChannelsByIds(ownerIds)
                        .associateBy { it.id }

                for (emote in newChannelDtos) {
                    val type =
                        when (emote.emoteType) {
                            "subscriptions" -> {
                                val channel = channelsByIdMap[emote.ownerId.toUserId()]
                                channel?.name?.let { EmoteType.ChannelTwitchEmote(it) } ?: EmoteType.GlobalTwitchEmote
                            }

                            "bitstier" -> {
                                val channel = channelsByIdMap[emote.ownerId.toUserId()]
                                channel?.name?.let { EmoteType.ChannelTwitchBitEmote(it) } ?: EmoteType.GlobalTwitchEmote
                            }

                            "follower" -> {
                                val channel = channelsByIdMap[emote.ownerId.toUserId()]
                                channel?.name?.let { EmoteType.ChannelTwitchFollowerEmote(it) } ?: EmoteType.GlobalTwitchEmote
                            }

                            else -> {
                                EmoteType.GlobalTwitchEmote
                            }
                        }
                    newGlobalEmotes.add(emote.toGenericEmote(type))
                }
            }

            if (newGlobalEmotes.isNotEmpty()) {
                allEmotes.addAll(newGlobalEmotes)
                globalEmoteState.update { it.copy(twitchEmotes = allEmotes.toList()) }
            }

            if (isFirstPage) {
                isFirstPage = false
                onFirstPageLoaded?.invoke()
            }
        }

        logger.debug { "Helix getUserEmotes: $totalCount total, ${seenIds.size} unique, ${allEmotes.size} resolved" }
    }

    suspend fun setChannelFollowerEmotes(
        channel: UserName,
        emotes: List<ChannelEmoteDto>,
    ) = withContext(dispatchersProvider.default) {
        val followerEmotes =
            emotes
                .filter { it.emoteType == "follower" }
                .map { it.toGenericEmote(EmoteType.ChannelTwitchFollowerEmote(channel)) }
        channelEmoteStates[channel]?.update {
            it.copy(twitchEmotes = followerEmotes)
        }
    }

    suspend fun setFFZEmotes(
        channel: UserName,
        ffzResult: FFZChannelDto,
    ) = withContext(dispatchersProvider.default) {
        val ffzEmotes =
            ffzResult.sets
                .flatMap { set ->
                    set.value.emotes.mapNotNull {
                        parseFFZEmote(it, channel)
                    }
                }
        channelEmoteStates[channel]?.update {
            it.copy(ffzEmotes = ffzEmotes)
        }
        ffzResult.room.modBadgeUrls?.let {
            val url = it["4"] ?: it["2"] ?: it["1"] ?: return@let
            ffzModBadges[channel] = "${url.withLeadingHttps}/rounded"
        }
        ffzResult.room.vipBadgeUrls?.let {
            val url = it["4"] ?: it["2"] ?: it["1"] ?: return@let
            ffzVipBadges[channel] = url.withLeadingHttps
        }
    }

    suspend fun setFFZGlobalEmotes(ffzResult: FFZGlobalDto) = withContext(dispatchersProvider.default) {
        val ffzGlobalEmotes =
            ffzResult.sets
                .filter { it.key in ffzResult.defaultSets }
                .flatMap { (_, emoteSet) ->
                    emoteSet.emotes.mapNotNull { emote ->
                        parseFFZEmote(emote, channel = null)
                    }
                }
        globalEmoteState.update { it.copy(ffzEmotes = ffzGlobalEmotes) }
    }

    suspend fun setBTTVEmotes(
        channel: UserName,
        channelDisplayName: DisplayName,
        bttvResult: BTTVChannelDto,
    ) = withContext(dispatchersProvider.default) {
        val bttvEmotes = (bttvResult.emotes + bttvResult.sharedEmotes).map { parseBTTVEmote(it, channelDisplayName) }
        channelEmoteStates[channel]?.update {
            it.copy(bttvEmotes = bttvEmotes)
        }
    }

    suspend fun setBTTVGlobalEmotes(globalEmotes: List<BTTVGlobalEmoteDto>) = withContext(dispatchersProvider.default) {
        val bttvGlobalEmotes = globalEmotes.map { parseBTTVGlobalEmote(it) }
        globalEmoteState.update { it.copy(bttvEmotes = bttvGlobalEmotes) }
    }

    suspend fun setSevenTVEmotes(
        channel: UserName,
        userDto: SevenTVUserDto,
    ) = withContext(dispatchersProvider.default) {
        val emoteSetId = userDto.emoteSet?.id ?: return@withContext
        val emoteList = userDto.emoteSet.emotes.orEmpty()

        sevenTvChannelDetails[channel] =
            SevenTVUserDetails(
                id = userDto.user.id,
                activeEmoteSetId = emoteSetId,
                connectionIndex = userDto.user.connections.indexOfFirst { it.platform == SevenTVUserConnection.twitch },
            )
        val sevenTvEmotes =
            emoteList
                .filterUnlistedIfEnabled()
                .mapNotNull { emote ->
                    parseSevenTVEmote(emote, EmoteType.ChannelSevenTVEmote(emote.data?.owner?.displayName, emote.data?.baseName?.takeIf { emote.name != it }))
                }

        channelEmoteStates[channel]?.update {
            it.copy(sevenTvEmotes = sevenTvEmotes)
        }
    }

    suspend fun setSevenTVEmoteSet(
        channel: UserName,
        emoteSet: SevenTVEmoteSetDto,
    ) = withContext(dispatchersProvider.default) {
        sevenTvChannelDetails[channel]?.let { details ->
            sevenTvChannelDetails[channel] = details.copy(activeEmoteSetId = emoteSet.id)
        }

        val sevenTvEmotes =
            emoteSet.emotes
                .orEmpty()
                .filterUnlistedIfEnabled()
                .mapNotNull { emote ->
                    parseSevenTVEmote(emote, EmoteType.ChannelSevenTVEmote(emote.data?.owner?.displayName, emote.data?.baseName?.takeIf { emote.name != it }))
                }

        channelEmoteStates[channel]?.update {
            it.copy(sevenTvEmotes = sevenTvEmotes)
        }
    }

    suspend fun updateSevenTVEmotes(
        channel: UserName,
        event: SevenTVEventMessage.EmoteSetUpdated,
    ) = withContext(dispatchersProvider.default) {
        val addedEmotes =
            event.added
                .filterUnlistedIfEnabled()
                .mapNotNull { emote ->
                    parseSevenTVEmote(emote, EmoteType.ChannelSevenTVEmote(emote.data?.owner?.displayName, emote.data?.baseName?.takeIf { emote.name != it }))
                }

        channelEmoteStates[channel]?.update { state ->
            val updated =
                state.sevenTvEmotes.mapNotNull { emote ->

                    if (event.removed.any { emote.id == it.id }) {
                        null
                    } else {
                        event.updated.find { emote.id == it.id }?.let { update ->
                            val mapNewBaseName = { oldBase: String? -> (oldBase ?: emote.code).takeIf { it != update.name } }
                            val newType =
                                when (emote.emoteType) {
                                    is EmoteType.ChannelSevenTVEmote -> emote.emoteType.copy(baseName = mapNewBaseName(emote.emoteType.baseName))
                                    is EmoteType.GlobalSevenTVEmote -> emote.emoteType.copy(baseName = mapNewBaseName(emote.emoteType.baseName))
                                    else -> emote.emoteType
                                }
                            emote.copy(code = update.name, emoteType = newType)
                        } ?: emote
                    }
                }
            state.copy(sevenTvEmotes = updated + addedEmotes)
        }
    }

    suspend fun setSevenTVGlobalEmotes(sevenTvResult: List<SevenTVEmoteDto>) = withContext(dispatchersProvider.default) {
        if (sevenTvResult.isEmpty()) return@withContext

        val sevenTvGlobalEmotes =
            sevenTvResult
                .filterUnlistedIfEnabled()
                .mapNotNull { emote ->
                    parseSevenTVEmote(emote, EmoteType.GlobalSevenTVEmote(emote.data?.owner?.displayName, emote.data?.baseName?.takeIf { emote.name != it }))
                }

        globalEmoteState.update { it.copy(sevenTvEmotes = sevenTvGlobalEmotes) }
    }

    suspend fun setCheermotes(
        channel: UserName,
        cheermoteDtos: List<CheermoteSetDto>,
    ) = withContext(dispatchersProvider.default) {
        val cheermoteSets =
            cheermoteDtos.map { dto ->
                CheermoteSet(
                    prefix = dto.prefix,
                    regex = Regex("^${Regex.escape(dto.prefix)}([1-9][0-9]*)$", RegexOption.IGNORE_CASE),
                    tiers =
                        dto.tiers
                            .sortedByDescending { it.minBits }
                            .map { tier ->
                                CheermoteTier(
                                    minBits = tier.minBits,
                                    color =
                                        try {
                                            tier.color.toColorInt()
                                        } catch (_: IllegalArgumentException) {
                                            Color.GRAY
                                        },
                                    animatedUrl =
                                        tier.images.dark.animated["2"] ?: tier.images.dark.animated["1"]
                                            .orEmpty(),
                                    staticUrl =
                                        tier.images.dark.static["2"] ?: tier.images.dark.static["1"]
                                            .orEmpty(),
                                )
                            },
                )
            }
        channelEmoteStates[channel]?.update {
            it.copy(cheermoteSets = cheermoteSets)
        }
    }

    private fun parseNonTwitchEmotes(
        message: String,
        channel: UserName,
        excludeCodes: Set<String>,
        hasBits: Boolean,
    ): Pair<List<ChatMessageEmote>, List<ChatMessageEmote>> {
        val emoteMap = getOrBuildEmoteMap(channel, withTwitch = false)
        val cheermoteSets = if (hasBits) {
            channelEmoteStates[channel]?.value?.cheermoteSets.orEmpty()
        } else {
            emptyList()
        }

        val thirdPartyEmotes = mutableListOf<ChatMessageEmote>()
        val cheermotes = mutableListOf<ChatMessageEmote>()

        message.forEachWord { word, startIndex ->
            var matchedCheermote = false
            if (cheermoteSets.isNotEmpty()) {
                for (set in cheermoteSets) {
                    val match = set.regex.matchEntire(word)
                    if (match != null) {
                        val bits = match.groupValues[1].toIntOrNull() ?: break
                        val tier = set.tiers.firstOrNull { bits >= it.minBits } ?: break
                        cheermotes +=
                            ChatMessageEmote(
                                position = startIndex..startIndex + word.length,
                                url = tier.animatedUrl,
                                id = "${set.prefix}_$bits",
                                code = word,
                                scale = 1,
                                type = ChatMessageEmoteType.Cheermote,
                                cheerAmount = bits,
                                cheerColor = tier.color,
                            )
                        matchedCheermote = true
                        break
                    }
                }
            }
            if (!matchedCheermote && word !in excludeCodes) {
                emoteMap[word]?.let { emote ->
                    thirdPartyEmotes +=
                        ChatMessageEmote(
                            position = startIndex..startIndex + word.length,
                            url = emote.url,
                            id = emote.id,
                            code = emote.code,
                            scale = emote.scale,
                            type = emote.emoteType.toChatMessageEmoteType(),
                            isOverlayEmote = emote.isOverlayEmote,
                        )
                }
            }
        }

        return thirdPartyEmotes to cheermotes
    }

    private fun ChannelEmoteDto.toGenericEmote(type: EmoteType): GenericEmote = GenericEmote(
        code = name,
        url = TWITCH_EMOTE_TEMPLATE.format(Locale.ROOT, id, TWITCH_EMOTE_SIZE),
        lowResUrl = TWITCH_EMOTE_TEMPLATE.format(Locale.ROOT, id, TWITCH_LOW_RES_EMOTE_SIZE),
        id = id,
        scale = 1,
        emoteType = type,
    )

    private fun UserEmoteDto.toGenericEmote(type: EmoteType): GenericEmote {
        val code =
            when (type) {
                is EmoteType.GlobalTwitchEmote -> EMOTE_REPLACEMENTS[name] ?: name
                else -> name
            }
        return GenericEmote(
            code = code,
            url = TWITCH_EMOTE_TEMPLATE.format(Locale.ROOT, id, TWITCH_EMOTE_SIZE),
            lowResUrl = TWITCH_EMOTE_TEMPLATE.format(Locale.ROOT, id, TWITCH_LOW_RES_EMOTE_SIZE),
            id = id,
            scale = 1,
            emoteType = type,
        )
    }

    @VisibleForTesting
    internal fun adjustOverlayEmotes(
        message: String,
        emotes: List<ChatMessageEmote>,
        trackTextEdits: Boolean = false,
    ): OverlayAdjustmentResult {
        var adjustedMessage = message
        val adjustedEmotes = emotes.sortedBy { it.position.first }.toMutableList()
        val textEdits = mutableListOf<PositionedTextEdit>()

        for (i in adjustedEmotes.lastIndex downTo 0) {
            val emote = adjustedEmotes[i]

            if (emote.isOverlayEmote) {
                var foundEmote = false
                var distanceToRegularEmote = 1 // initial space
                // first, iterate over previous emotes until a regular emote is found
                for (j in i - 1 downTo 0) {
                    val previousEmote = adjustedEmotes[j]
                    if (previousEmote.isOverlayEmote) {
                        distanceToRegularEmote += previousEmote.code.length + 1 // emote code + space
                        continue
                    }

                    val actualDistanceToRegularEmote = emote.position.first - previousEmote.position.last

                    // The "distance" between the found non-overlay emote and the current overlay emote does not match the expected, valid distance
                    // This means, that there are non-emote "words" in-between, and we should not adjust this overlay emote
                    // Example: FeelsDankMan asd cvHazmat RainTime
                    // actualDistanceToRegularEmote = 14 != distanceToRegularEmote = 10 -> break
                    if (actualDistanceToRegularEmote != distanceToRegularEmote) {
                        break
                    }

                    val removalEndExclusive =
                        when (emote.position.last) {
                            adjustedMessage.length -> adjustedMessage.length
                            else -> (emote.position.last + 1).coerceAtMost(adjustedMessage.length)
                        }
                    adjustedMessage = adjustedMessage.removeRange(emote.position.first, removalEndExclusive)
                    if (trackTextEdits) {
                        textEdits += PositionedTextEdit(emote.position.first, removalEndExclusive, 0)
                    }
                    adjustedEmotes[i] = emote.copy(position = previousEmote.position)
                    foundEmote = true

                    break
                }

                if (foundEmote) {
                    // iterate forward to fix future emote positions
                    for (k in i + 1..adjustedEmotes.lastIndex) {
                        val nextEmote = adjustedEmotes[k]
                        if (emote.position.first >= nextEmote.position.first) {
                            continue
                        }

                        val first = nextEmote.position.first - emote.code.length - 1
                        val last = nextEmote.position.last - emote.code.length - 1
                        adjustedEmotes[k] = nextEmote.copy(position = first..last)
                    }
                }
            }
        }

        return OverlayAdjustmentResult(adjustedMessage, adjustedEmotes, textEdits)
    }

    internal data class OverlayAdjustmentResult(
        val message: String,
        val emotes: List<ChatMessageEmote>,
        val textEdits: List<PositionedTextEdit>,
    )

    private fun duplicateWhitespaceEdits(message: String): List<PositionedTextEdit> = buildList {
        var previousWhitespace = false
        var offset = 0
        while (offset < message.length) {
            val codePoint = message.codePointAt(offset)
            val length = Character.charCount(codePoint)
            val isWhitespace = Character.isWhitespace(codePoint) || codePoint == 0x3164
            if (previousWhitespace && isWhitespace) {
                add(PositionedTextEdit(offset, offset + length, 0))
            }
            previousWhitespace = isWhitespace
            offset += length
        }
    }

    /**
     * Counts elements in a sorted list that are strictly less than [value] using binary search.
     */
    @VisibleForTesting
    internal fun countLessThan(
        sortedList: List<Int>,
        value: Int,
    ): Int {
        var low = 0
        var high = sortedList.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (sortedList[mid] < value) low = mid + 1 else high = mid
        }
        return low
    }

    @VisibleForTesting
    internal fun parseTwitchEmotes(
        emotesWithPositions: List<EmoteWithPositions>,
        message: String,
        supplementaryCodePointPositions: List<Int>,
        appendedSpaces: List<Int>,
        removedSpaces: List<Int>,
        replyMentionOffset: Int,
    ): List<ChatMessageEmote> = emotesWithPositions.flatMap { (id, positions) ->
        positions.mapNotNull { range ->
            // Twitch positions include the reply mention prefix, but our message/positions are stripped.
            // Subtract replyMentionOffset first so lookups align with the stripped message.
            val adjustedFirst = range.first - replyMentionOffset
            val adjustedLast = range.last - replyMentionOffset
            val removedSpaceExtra = countLessThan(removedSpaces, adjustedFirst)
            val unicodeExtra = countLessThan(supplementaryCodePointPositions, adjustedFirst - removedSpaceExtra)
            val spaceExtra = countLessThan(appendedSpaces, adjustedFirst + unicodeExtra)
            val fixedStart = adjustedFirst + unicodeExtra + spaceExtra - removedSpaceExtra
            val fixedEnd = adjustedLast + unicodeExtra + spaceExtra - removedSpaceExtra

            // be extra safe in case twitch sends invalid emote ranges :)
            val fixedPos = fixedStart.coerceAtLeast(minimumValue = 0)..(fixedEnd + 1).coerceAtMost(message.length)
            if (fixedPos.first >= fixedPos.last) {
                return@mapNotNull null
            }
            val code = message.substring(fixedPos.first, fixedPos.last)
            ChatMessageEmote(
                position = fixedPos,
                url = TWITCH_EMOTE_TEMPLATE.format(Locale.ROOT, id, TWITCH_EMOTE_SIZE),
                id = id,
                code = code,
                scale = 1,
                type = ChatMessageEmoteType.TwitchEmote,
                isTwitch = true,
            )
        }
    }

    private fun parseBTTVEmote(
        emote: BTTVEmoteDto,
        channelDisplayName: DisplayName,
    ): GenericEmote {
        val name = emote.code
        val id = emote.id
        val url = BTTV_EMOTE_TEMPLATE.format(Locale.ROOT, id, BTTV_EMOTE_SIZE)
        val lowResUrl = BTTV_EMOTE_TEMPLATE.format(Locale.ROOT, id, BTTV_LOW_RES_EMOTE_SIZE)
        return GenericEmote(
            code = name,
            url = url,
            lowResUrl = lowResUrl,
            id = id,
            scale = 1,
            emoteType = EmoteType.ChannelBTTVEmote(emote.user?.displayName ?: channelDisplayName, isShared = emote.user != null),
            isOverlayEmote = name in OVERLAY_EMOTES,
        )
    }

    private fun parseBTTVGlobalEmote(emote: BTTVGlobalEmoteDto): GenericEmote {
        val name = emote.code
        val id = emote.id
        val url = BTTV_EMOTE_TEMPLATE.format(Locale.ROOT, id, BTTV_EMOTE_SIZE)
        val lowResUrl = BTTV_EMOTE_TEMPLATE.format(Locale.ROOT, id, BTTV_LOW_RES_EMOTE_SIZE)
        return GenericEmote(
            code = name,
            url = url,
            lowResUrl = lowResUrl,
            id = id,
            scale = 1,
            emoteType = EmoteType.GlobalBTTVEmote,
            isOverlayEmote = name in OVERLAY_EMOTES,
        )
    }

    private fun parseFFZEmote(
        emote: FFZEmoteDto,
        channel: UserName?,
    ): GenericEmote? {
        val name = emote.name
        val id = emote.id
        val urlMap = emote.animated ?: emote.urls

        val (scale, url) =
            when {
                urlMap["4"] != null -> 1 to urlMap.getValue("4")
                urlMap["2"] != null -> 2 to urlMap.getValue("2")
                else -> 4 to urlMap["1"]
            }
        url ?: return null
        val lowResUrl = urlMap["2"] ?: urlMap["1"] ?: return null
        val type =
            when (channel) {
                null -> EmoteType.GlobalFFZEmote(emote.owner?.displayName)
                else -> EmoteType.ChannelFFZEmote(emote.owner?.displayName)
            }
        return GenericEmote(name, url.withLeadingHttps, lowResUrl.withLeadingHttps, "$id", scale, type)
    }

    private fun parseSevenTVEmote(
        emote: SevenTVEmoteDto,
        type: EmoteType,
    ): GenericEmote? {
        val data = emote.data ?: return null
        if (data.isTwitchDisallowed) {
            return null
        }

        val base = "${data.host.url}/".withLeadingHttps
        val urls =
            data.host.files
                .filter { it.format == "WEBP" }
                .associate {
                    val size = it.name.substringBeforeLast('.')
                    size to it.emoteUrlWithFallback(base)
                }

        return GenericEmote(
            code = emote.name,
            url = urls["4x"] ?: return null,
            lowResUrl = urls["2x"] ?: urls["1x"] ?: return null,
            id = emote.id,
            scale = 1,
            emoteType = type,
            isOverlayEmote = emote.isZeroWidth,
        )
    }

    private fun SevenTVEmoteFileDto.emoteUrlWithFallback(base: String): String = "$base$name"

    private suspend fun List<SevenTVEmoteDto>.filterUnlistedIfEnabled(): List<SevenTVEmoteDto> = when {
        chatSettingsDataStore.settings.first().allowUnlistedSevenTvEmotes -> this
        else -> filter { it.data?.listed == true }
    }

    private val String.withLeadingHttps: String
        get() =
            when {
                startsWith(prefix = "https:") -> this
                else -> "https:$this"
            }

    // Matches the \s character class: split(WHITESPACE_REGEX) semantics without the regex and word-list allocations
    private fun Char.isWordSeparator(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\u000B' || this == '\u000C' || this == '\r'

    private inline fun String.forEachWord(action: (word: String, startIndex: Int) -> Unit) {
        var wordStart = 0
        for (index in indices) {
            if (this[index].isWordSeparator()) {
                if (index > wordStart) {
                    action(substring(wordStart, index), wordStart)
                }
                wordStart = index + 1
            }
        }
        if (length > wordStart) {
            action(substring(wordStart), wordStart)
        }
    }

    companion object {
        private val ESCAPE_TAG = 0x000E0002.codePointAsString
        val ESCAPE_TAG_REGEX = "(?<!$ESCAPE_TAG)$ESCAPE_TAG".toRegex()
        const val ZERO_WIDTH_JOINER = 0x200D.toChar().toString()

        private val CHANNEL_EMOTE_TYPES = setOf("subscriptions", "bitstier", "follower")

        private const val TWITCH_EMOTE_TEMPLATE = "https://static-cdn.jtvnw.net/emoticons/v2/%s/default/dark/%s"
        private const val TWITCH_EMOTE_SIZE = "3.0"
        private const val TWITCH_LOW_RES_EMOTE_SIZE = "2.0"

        private const val BTTV_EMOTE_TEMPLATE = "https://cdn.betterttv.net/emote/%s/%s"
        private const val BTTV_EMOTE_SIZE = "3x"
        private const val BTTV_LOW_RES_EMOTE_SIZE = "2x"

        private val EMOTE_REPLACEMENTS =
            mapOf(
                "[oO](_|\\.)[oO]" to "O_o",
                "\\&lt\\;3" to "<3",
                "\\:-?(p|P)" to ":P",
                "\\:-?[z|Z|\\|]" to ":Z",
                "\\:-?\\)" to ":)",
                "\\;-?(p|P)" to ";P",
                "R-?\\)" to "R)",
                "\\&gt\\;\\(" to ">(",
                "\\:-?(o|O)" to ":O",
                "\\:-?[\\\\/]" to ":/",
                "\\:-?\\(" to ":(",
                "\\:-?D" to ":D",
                "\\;-?\\)" to ";)",
                "B-?\\)" to "B)",
                "#-?[\\/]" to "#/",
                ":-?(?:7|L)" to ":7",
                "\\&lt\\;\\]" to "<]",
                "\\:-?(S|s)" to ":s",
                "\\:\\&gt\\;" to ":>",
            )
        private val OVERLAY_EMOTES =
            listOf(
                "SoSnowy",
                "IceCold",
                "SantaHat",
                "TopHat",
                "ReinDeer",
                "CandyCane",
                "cvMask",
                "cvHazmat",
            )
    }
}
