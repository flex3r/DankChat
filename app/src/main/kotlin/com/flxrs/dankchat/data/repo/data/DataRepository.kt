package com.flxrs.dankchat.data.repo.data

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.badges.BadgesApiClient
import com.flxrs.dankchat.data.api.bttv.liveupdates.BTTVLiveUpdateClient
import com.flxrs.dankchat.data.api.bttv.liveupdates.BTTVLiveUpdateEvent
import com.flxrs.dankchat.data.api.cache.CachedEmoteProvider
import com.flxrs.dankchat.data.api.cache.CachedResult
import com.flxrs.dankchat.data.api.dankchat.DankChatApiClient
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.dto.StreamDto
import com.flxrs.dankchat.data.api.helix.dto.UserFollowsDto
import com.flxrs.dankchat.data.api.seventv.SevenTVApiClient
import com.flxrs.dankchat.data.api.seventv.eventapi.SevenTVEventApiClient
import com.flxrs.dankchat.data.api.seventv.eventapi.SevenTVEventMessage
import com.flxrs.dankchat.data.api.upload.UploadClient
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.RecentUploadsRepository
import com.flxrs.dankchat.data.repo.emote.EmoteRepository
import com.flxrs.dankchat.data.repo.emote.Emotes
import com.flxrs.dankchat.data.twitch.badge.toBadgeSets
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.chat.VisibleThirdPartyEmotes
import com.flxrs.dankchat.utils.extensions.measureTimeAndLog
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger("DataRepository")

@Single
class DataRepository(
    private val helixApiClient: HelixApiClient,
    private val dankChatApiClient: DankChatApiClient,
    private val badgesApiClient: BadgesApiClient,
    private val cachedEmoteProvider: CachedEmoteProvider,
    private val sevenTVApiClient: SevenTVApiClient,
    private val sevenTVEventApiClient: SevenTVEventApiClient,
    private val bttvLiveUpdateClient: BTTVLiveUpdateClient,
    private val uploadClient: UploadClient,
    private val emoteRepository: EmoteRepository,
    private val recentUploadsRepository: RecentUploadsRepository,
    private val authDataStore: AuthDataStore,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val _dataLoadingFailures = MutableStateFlow(emptySet<DataLoadingFailure>())
    private val _dataUpdateEvents = MutableSharedFlow<DataUpdateEventMessage>()
    private val serviceEventChannel = Channel<ServiceEvent>(Channel.BUFFERED)
    private val bttvChannels = ConcurrentHashMap<UserId, UserName>()

    init {
        scope.launch {
            sevenTVEventApiClient.messages.collect { event ->
                when (event) {
                    is SevenTVEventMessage.UserUpdated -> {
                        val channel = emoteRepository.getChannelForSevenTVEmoteSet(event.oldEmoteSetId) ?: return@collect
                        val details = emoteRepository.getSevenTVUserDetails(channel) ?: return@collect
                        if (details.connectionIndex != event.connectionIndex) {
                            return@collect
                        }

                        val newEmoteSet = sevenTVApiClient.getSevenTVEmoteSet(event.emoteSetId).getOrNull() ?: return@collect
                        emoteRepository.setSevenTVEmoteSet(channel, newEmoteSet)

                        sevenTVEventApiClient.unsubscribeEmoteSet(event.oldEmoteSetId)
                        sevenTVEventApiClient.subscribeEmoteSet(event.emoteSetId)
                        _dataUpdateEvents.emit(DataUpdateEventMessage.ActiveEmoteSetChanged(channel, event.actorName, newEmoteSet.name))
                    }

                    is SevenTVEventMessage.EmoteSetUpdated -> {
                        val channel = emoteRepository.getChannelForSevenTVEmoteSet(event.emoteSetId) ?: return@collect
                        emoteRepository.updateSevenTVEmotes(channel, event)
                        _dataUpdateEvents.emit(DataUpdateEventMessage.EmoteSetUpdated(channel, event))
                    }
                }
            }
        }
        scope.launch {
            bttvLiveUpdateClient.events.collect { event ->
                val channel = bttvChannels[event.channelId] ?: return@collect
                when (event) {
                    is BTTVLiveUpdateEvent.EmoteAdded -> {
                        if (emoteRepository.addBTTVEmote(channel, event.emote)) {
                            _dataUpdateEvents.emit(DataUpdateEventMessage.BTTVEmoteAdded(channel, event.emote.code))
                        }
                    }

                    is BTTVLiveUpdateEvent.EmoteUpdated -> {
                        val (oldName, newName) = emoteRepository.updateBTTVEmote(channel, event.emote) ?: return@collect
                        if (oldName != newName) {
                            _dataUpdateEvents.emit(DataUpdateEventMessage.BTTVEmoteRenamed(channel, oldName, newName))
                        }
                    }

                    is BTTVLiveUpdateEvent.EmoteRemoved -> {
                        val emoteName = emoteRepository.removeBTTVEmote(channel, event.emoteId) ?: return@collect
                        _dataUpdateEvents.emit(DataUpdateEventMessage.BTTVEmoteRemoved(channel, emoteName))
                    }
                }
            }
        }
    }

    val serviceEvents = serviceEventChannel.receiveAsFlow()
    val dataLoadingFailures = _dataLoadingFailures.asStateFlow()
    val dataUpdateEvents = _dataUpdateEvents.asSharedFlow()

    fun clearDataLoadingFailures() = _dataLoadingFailures.update { emptySet() }

    fun getEmotes(channel: UserName): Flow<Emotes> = emoteRepository.getEmotes(channel)

    fun createFlowsIfNecessary(channels: List<UserName>) = emoteRepository.createFlowsIfNecessary(channels)

    suspend fun getChannelFollowers(
        broadcasterId: UserId,
        targetId: UserId,
    ): UserFollowsDto? = helixApiClient.getChannelFollowers(broadcasterId, targetId).getOrNull()

    suspend fun getStreams(channels: List<UserName>): List<StreamDto>? = helixApiClient.getStreams(channels).getOrNull()

    suspend fun reconnect() {
        sevenTVEventApiClient.reconnect()
        bttvLiveUpdateClient.reconnect()
    }

    suspend fun reconnectIfNecessary() {
        sevenTVEventApiClient.reconnectIfNecessary()
        bttvLiveUpdateClient.reconnectIfNecessary()
    }

    suspend fun removeChannels(removed: List<UserName>) {
        removed.forEach { channel ->
            val details = emoteRepository.getSevenTVUserDetails(channel) ?: return@forEach
            sevenTVEventApiClient.unsubscribeUser(details.id)
            sevenTVEventApiClient.unsubscribeEmoteSet(details.activeEmoteSetId)
        }
        bttvChannels.entries
            .filter { it.value in removed }
            .map { it.key }
            .forEach { channelId ->
                bttvChannels.remove(channelId)
                bttvLiveUpdateClient.unsubscribeChannel(channelId)
            }
    }

    suspend fun uploadMedia(file: File): Result<String> = uploadClient.uploadMedia(file).mapCatching {
        recentUploadsRepository.addUpload(it)
        it.imageLink
    }

    suspend fun loadGlobalBadges(): Result<Unit> = withContext(dispatchersProvider.io) {
        measureTimeAndLog(logger, "global badges") {
            val result =
                when {
                    authDataStore.isLoggedIn -> helixApiClient.getGlobalBadges().map { it.toBadgeSets() }
                    System.currentTimeMillis() < BADGES_SUNSET_MILLIS -> badgesApiClient.getGlobalBadges().map { it.toBadgeSets() }
                    else -> return@withContext Result.success(Unit)
                }.getOrEmitFailure { DataLoadingStep.GlobalBadges }
            result.onSuccess { emoteRepository.setGlobalBadges(it) }.map { }
        }
    }

    suspend fun loadDankChatBadges(): Result<Unit> = withContext(dispatchersProvider.io) {
        measureTimeAndLog(logger, "DankChat badges") {
            dankChatApiClient
                .getDankChatBadges()
                .getOrEmitFailure { DataLoadingStep.DankChatBadges }
                .onSuccess { emoteRepository.setDankChatBadges(it) }
                .map { }
        }
    }

    suspend fun loadUserEmotes(
        userId: UserId,
        onFirstPageLoaded: (() -> Unit)? = null,
    ): Result<Unit> = emoteRepository
        .loadUserEmotes(userId, onFirstPageLoaded)
        .getOrEmitFailure { DataLoadingStep.TwitchEmotes }

    suspend fun sendShutdownCommand() {
        serviceEventChannel.send(ServiceEvent.Shutdown)
    }

    suspend fun loadChannelBadges(
        channel: UserName,
        id: UserId,
    ): Result<Unit> = withContext(dispatchersProvider.io) {
        measureTimeAndLog(logger, "channel badges for #$id") {
            val result =
                when {
                    authDataStore.isLoggedIn -> helixApiClient.getChannelBadges(id).map { it.toBadgeSets() }
                    System.currentTimeMillis() < BADGES_SUNSET_MILLIS -> badgesApiClient.getChannelBadges(id).map { it.toBadgeSets() }
                    else -> return@withContext Result.success(Unit)
                }.getOrEmitFailure { DataLoadingStep.ChannelBadges(channel, id) }
            result.onSuccess { emoteRepository.setChannelBadges(channel, it) }.map { }
        }
    }

    suspend fun loadChannelFFZEmotes(
        channel: UserName,
        channelId: UserId,
        forceNetwork: Boolean = false,
    ): Result<EmoteLoadResult> = withContext(dispatchersProvider.io) {
        if (VisibleThirdPartyEmotes.FFZ !in chatSettingsDataStore.settings.first().visibleEmotes) {
            return@withContext Result.success(EmoteLoadResult.Loaded)
        }

        measureTimeAndLog(logger, "FFZ emotes for #$channel") {
            collectCachedEmotes(
                flow = cachedEmoteProvider.getFFZChannelEmotes(channelId, forceNetwork),
                onData = { emoteRepository.setFFZEmotes(channel, it) },
                onFailure = { getOrEmitFailure { DataLoadingStep.ChannelFFZEmotes(channel, channelId) } },
            )
        }
    }

    suspend fun loadChannelBTTVEmotes(
        channel: UserName,
        channelDisplayName: DisplayName,
        channelId: UserId,
        forceNetwork: Boolean = false,
    ): Result<EmoteLoadResult> = withContext(dispatchersProvider.io) {
        if (VisibleThirdPartyEmotes.BTTV !in chatSettingsDataStore.settings.first().visibleEmotes) {
            return@withContext Result.success(EmoteLoadResult.Loaded)
        }

        measureTimeAndLog(logger, "BTTV emotes for #$channel") {
            collectCachedEmotes(
                flow = cachedEmoteProvider.getBTTVChannelEmotes(channelId, forceNetwork),
                onData = {
                    emoteRepository.setBTTVEmotes(channel, channelDisplayName, it)
                    bttvChannels[channelId] = channel
                    bttvLiveUpdateClient.subscribeChannel(channelId)
                },
                onFailure = { getOrEmitFailure { DataLoadingStep.ChannelBTTVEmotes(channel, channelDisplayName, channelId) } },
            )
        }
    }

    suspend fun loadChannelSevenTVEmotes(
        channel: UserName,
        channelId: UserId,
        forceNetwork: Boolean = false,
    ): Result<EmoteLoadResult> = withContext(dispatchersProvider.io) {
        if (VisibleThirdPartyEmotes.SevenTV !in chatSettingsDataStore.settings.first().visibleEmotes) {
            return@withContext Result.success(EmoteLoadResult.Loaded)
        }

        measureTimeAndLog(logger, "7TV emotes for #$channel") {
            collectCachedEmotes(
                flow = cachedEmoteProvider.getSevenTVChannelEmotes(channelId, forceNetwork),
                onData = { result ->
                    if (result.emoteSet?.id != null) {
                        sevenTVEventApiClient.subscribeEmoteSet(result.emoteSet.id)
                    }
                    sevenTVEventApiClient.subscribeUser(result.user.id)
                    emoteRepository.setSevenTVEmotes(channel, result)
                },
                onFailure = { getOrEmitFailure { DataLoadingStep.ChannelSevenTVEmotes(channel, channelId) } },
            )
        }
    }

    suspend fun loadChannelCheermotes(
        channel: UserName,
        channelId: UserId,
    ): Result<Unit> = withContext(dispatchersProvider.io) {
        if (!authDataStore.isLoggedIn) {
            return@withContext Result.success(Unit)
        }

        measureTimeAndLog(logger, "cheermotes for #$channel") {
            helixApiClient
                .getCheermotes(channelId)
                .getOrEmitFailure { DataLoadingStep.ChannelCheermotes(channel, channelId) }
                .onSuccess { emoteRepository.setCheermotes(channel, it) }
                .map { }
        }
    }

    suspend fun loadChannelFollowerEmotes(
        channel: UserName,
        channelId: UserId,
    ): Result<Unit> = withContext(dispatchersProvider.io) {
        val userId = authDataStore.userIdString
        if (!authDataStore.isLoggedIn || userId == null) {
            return@withContext Result.success(Unit)
        }

        measureTimeAndLog(logger, "follower emotes for #$channel") {
            helixApiClient
                .getFollowedChannel(userId, channelId)
                .fold(
                    // Subscribers already receive follower emotes via the user emotes endpoint,
                    // merging dedups them, so the channel emotes are fetched for all followers
                    onSuccess = { followed ->
                        when (followed) {
                            null -> Result.success(emptyList())
                            else -> helixApiClient.getChannelEmotes(channelId)
                        }
                    },
                    onFailure = { Result.failure(it) },
                ).getOrEmitFailure { DataLoadingStep.ChannelFollowerEmotes(channel, channelId) }
                .onSuccess { emoteRepository.setChannelFollowerEmotes(channel, it) }
                .map { }
        }
    }

    suspend fun loadGlobalFFZEmotes(forceNetwork: Boolean = false): Result<Unit> = withContext(dispatchersProvider.io) {
        if (VisibleThirdPartyEmotes.FFZ !in chatSettingsDataStore.settings.first().visibleEmotes) {
            return@withContext Result.success(Unit)
        }

        measureTimeAndLog(logger, "global FFZ emotes") {
            collectCachedEmotes(
                flow = cachedEmoteProvider.getFFZGlobalEmotes(forceNetwork),
                onData = { emoteRepository.setFFZGlobalEmotes(it) },
                onFailure = { getOrEmitFailure { DataLoadingStep.GlobalFFZEmotes } },
            ).map { }
        }
    }

    suspend fun loadGlobalBTTVEmotes(forceNetwork: Boolean = false): Result<Unit> = withContext(dispatchersProvider.io) {
        if (VisibleThirdPartyEmotes.BTTV !in chatSettingsDataStore.settings.first().visibleEmotes) {
            return@withContext Result.success(Unit)
        }

        measureTimeAndLog(logger, "global BTTV emotes") {
            collectCachedEmotes(
                flow = cachedEmoteProvider.getBTTVGlobalEmotes(forceNetwork),
                onData = { emoteRepository.setBTTVGlobalEmotes(it) },
                onFailure = { getOrEmitFailure { DataLoadingStep.GlobalBTTVEmotes } },
            ).map { }
        }
    }

    suspend fun loadGlobalSevenTVEmotes(forceNetwork: Boolean = false): Result<Unit> = withContext(dispatchersProvider.io) {
        if (VisibleThirdPartyEmotes.SevenTV !in chatSettingsDataStore.settings.first().visibleEmotes) {
            return@withContext Result.success(Unit)
        }

        measureTimeAndLog(logger, "global 7TV emotes") {
            collectCachedEmotes(
                flow = cachedEmoteProvider.getSevenTVGlobalEmotes(forceNetwork),
                onData = { emoteRepository.setSevenTVGlobalEmotes(it) },
                onFailure = { getOrEmitFailure { DataLoadingStep.GlobalSevenTVEmotes } },
            ).map { }
        }
    }

    private suspend fun <T : Any> collectCachedEmotes(
        flow: Flow<CachedResult<T?>>,
        onData: suspend (T) -> Unit,
        onFailure: Result<EmoteLoadResult>.() -> Unit,
    ): Result<EmoteLoadResult> {
        var loadResult: Result<EmoteLoadResult> = Result.success(EmoteLoadResult.Loaded)
        flow.collect { result ->
            when (result) {
                is CachedResult.Success -> {
                    result.data?.let { onData(it) }
                }

                is CachedResult.CachedFallback -> {
                    logger.warn(result.error) { "Using cached emotes as fallback" }
                    loadResult = Result.success(EmoteLoadResult.CachedFallback)
                }

                is CachedResult.Failure -> {
                    loadResult = Result.failure(result.error)
                    loadResult.onFailure()
                }
            }
        }
        return loadResult
    }

    private fun <T> Result<T>.getOrEmitFailure(step: () -> DataLoadingStep): Result<T> = onFailure { throwable ->
        val loadingStep = step()
        logger.error(throwable) { "Data request failed [$loadingStep]" }
        _dataLoadingFailures.update { it + DataLoadingFailure(loadingStep, throwable) }
    }

    companion object {
        private const val BADGES_SUNSET_MILLIS = 1685637000000L // 2023-06-01 16:30:00
    }
}
