package com.flxrs.dankchat.data.repo.data

import android.util.Log
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.badges.BadgesApiClient
import com.flxrs.dankchat.data.api.bttv.BTTVApiClient
import com.flxrs.dankchat.data.api.dankchat.DankChatApiClient
import com.flxrs.dankchat.data.api.ffz.FFZApiClient
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.dto.StreamDto
import com.flxrs.dankchat.data.api.helix.dto.UserDto
import com.flxrs.dankchat.data.api.helix.dto.UserFollowsDto
import com.flxrs.dankchat.data.api.seventv.SevenTVApiClient
import com.flxrs.dankchat.data.api.seventv.eventapi.SevenTVEventApiClient
import com.flxrs.dankchat.data.api.seventv.eventapi.SevenTVEventMessage
import com.flxrs.dankchat.data.api.upload.UploadClient
import com.flxrs.dankchat.data.repo.RecentUploadsRepository
import com.flxrs.dankchat.data.repo.emote.EmoteRepository
import com.flxrs.dankchat.data.repo.emote.Emotes
import com.flxrs.dankchat.data.twitch.badge.toBadgeSets
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.chat.VisibleThirdPartyEmotes
import com.flxrs.dankchat.utils.extensions.measureTimeAndLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File
import kotlin.system.measureTimeMillis

@Single
class DataRepository(
    private val helixApiClient: HelixApiClient,
    private val dankChatApiClient: DankChatApiClient,
    private val badgesApiClient: BadgesApiClient,
    private val ffzApiClient: FFZApiClient,
    private val bttvApiClient: BTTVApiClient,
    private val sevenTVApiClient: SevenTVApiClient,
    private val sevenTVEventApiClient: SevenTVEventApiClient,
    private val uploadClient: UploadClient,
    private val emoteRepository: EmoteRepository,
    private val recentUploadsRepository: RecentUploadsRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    dispatchersProvider: DispatchersProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val _dataLoadingFailures = MutableStateFlow(emptySet<DataLoadingFailure>())
    private val _dataUpdateEvents = MutableSharedFlow<DataUpdateEventMessage>()
    private val serviceEventChannel = Channel<ServiceEvent>(Channel.BUFFERED)

    init {
        scope.launch {
            sevenTVEventApiClient.messages.collect { event ->
                when (event) {
                    is SevenTVEventMessage.UserUpdated     -> {
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
    }

    val serviceEvents = serviceEventChannel.receiveAsFlow()
    val dataLoadingFailures = _dataLoadingFailures.asStateFlow()
    val dataUpdateEvents = _dataUpdateEvents.asSharedFlow()

    fun clearDataLoadingFailures() = _dataLoadingFailures.update { emptySet() }

    fun getEmotes(channel: UserName): StateFlow<Emotes> = emoteRepository.getEmotes(channel)
    fun createFlowsIfNecessary(channels: List<UserName>) = emoteRepository.createFlowsIfNecessary(channels)

    suspend fun getUser(userId: UserId): UserDto? = helixApiClient.getUser(userId).getOrNull()
    suspend fun getUserByName(name: UserName): UserDto? = helixApiClient.getUserByName(name).getOrNull()
    suspend fun getUsersByNames(names: List<UserName>): List<UserDto> = helixApiClient.getUsersByNames(names).getOrNull().orEmpty()
    suspend fun getChannelFollowers(broadcasterId: UserId, targetId: UserId): UserFollowsDto? = helixApiClient.getChannelFollowers(broadcasterId, targetId).getOrNull()
    suspend fun getStreams(channels: List<UserName>): List<StreamDto>? = helixApiClient.getStreams(channels).getOrNull()

    fun reconnect() {
        sevenTVEventApiClient.reconnect()
    }

    fun reconnectIfNecessary() {
        sevenTVEventApiClient.reconnectIfNecessary()
    }

    fun removeChannels(removed: List<UserName>) {
        removed.forEach { channel ->
            val details = emoteRepository.getSevenTVUserDetails(channel) ?: return@forEach
            sevenTVEventApiClient.unsubscribeUser(details.id)
            sevenTVEventApiClient.unsubscribeEmoteSet(details.activeEmoteSetId)
        }
    }

    suspend fun uploadMedia(file: File): Result<String> = uploadClient.uploadMedia(file).mapCatching {
        recentUploadsRepository.addUpload(it)
        it.imageLink
    }

    suspend fun loadGlobalBadges() = withContext(Dispatchers.IO) {
        measureTimeAndLog(TAG, "global badges") {
            val badges = when {
                dankChatPreferenceStore.isLoggedIn                -> helixApiClient.getGlobalBadges().map { it.toBadgeSets() }
                System.currentTimeMillis() < BADGES_SUNSET_MILLIS -> badgesApiClient.getGlobalBadges().map { it.toBadgeSets() }
                else                                              -> return@withContext
            }.getOrEmitFailure { DataLoadingStep.GlobalBadges }
            badges?.also { emoteRepository.setGlobalBadges(it) }
        }
    }

    suspend fun loadDankChatBadges() = withContext(Dispatchers.IO) {
        measureTimeMillis {
            dankChatApiClient.getDankChatBadges()
                .getOrEmitFailure { DataLoadingStep.DankChatBadges }
                ?.let { emoteRepository.setDankChatBadges(it) }
        }.let { Log.i(TAG, "Loaded DankChat badges in $it ms") }
    }

    suspend fun loadUserStateEmotes(globalEmoteSetIds: List<String>, followerEmoteSetIds: Map<UserName, List<String>>) {
        emoteRepository.loadUserStateEmotes(globalEmoteSetIds, followerEmoteSetIds)
    }

    suspend fun sendShutdownCommand() {
        serviceEventChannel.send(ServiceEvent.Shutdown)
    }

    suspend fun loadChannelBadges(channel: UserName, id: UserId) = withContext(Dispatchers.IO) {
        measureTimeAndLog(TAG, "channel badges for #$id") {
            val badges = when {
                dankChatPreferenceStore.isLoggedIn                -> helixApiClient.getChannelBadges(id).map { it.toBadgeSets() }
                System.currentTimeMillis() < BADGES_SUNSET_MILLIS -> badgesApiClient.getChannelBadges(id).map { it.toBadgeSets() }
                else                                              -> return@withContext
            }.getOrEmitFailure { DataLoadingStep.ChannelBadges(channel, id) }
            badges?.also { emoteRepository.setChannelBadges(channel, it) }
        }
    }

    suspend fun loadChannelFFZEmotes(channel: UserName, channelId: UserId) = withContext(Dispatchers.IO) {
        if (VisibleThirdPartyEmotes.FFZ !in chatSettingsDataStore.current().visibleEmotes) {
            return@withContext
        }

        measureTimeMillis {
            ffzApiClient.getFFZChannelEmotes(channelId)
                .getOrEmitFailure { DataLoadingStep.ChannelFFZEmotes(channel, channelId) }
                ?.let { emoteRepository.setFFZEmotes(channel, it) }
        }.let { Log.i(TAG, "Loaded FFZ emotes for #$channel in $it ms") }
    }

    suspend fun loadChannelBTTVEmotes(channel: UserName, channelDisplayName: DisplayName, channelId: UserId) = withContext(Dispatchers.IO) {
        if (VisibleThirdPartyEmotes.BTTV !in chatSettingsDataStore.current().visibleEmotes) {
            return@withContext
        }

        measureTimeMillis {
            bttvApiClient.getBTTVChannelEmotes(channelId)
                .getOrEmitFailure { DataLoadingStep.ChannelBTTVEmotes(channel, channelDisplayName, channelId) }
                ?.let { emoteRepository.setBTTVEmotes(channel, channelDisplayName, it) }
        }.let { Log.i(TAG, "Loaded BTTV emotes for #$channel in $it ms") }
    }

    suspend fun loadChannelSevenTVEmotes(channel: UserName, channelId: UserId) = withContext(Dispatchers.IO) {
        if (VisibleThirdPartyEmotes.SevenTV !in chatSettingsDataStore.current().visibleEmotes) {
            return@withContext
        }

        measureTimeMillis {
            sevenTVApiClient.getSevenTVChannelEmotes(channelId)
                .getOrEmitFailure { DataLoadingStep.ChannelSevenTVEmotes(channel, channelId) }
                ?.let { result ->
                    if (result.emoteSet?.id != null) {
                        sevenTVEventApiClient.subscribeEmoteSet(result.emoteSet.id)
                    }
                    sevenTVEventApiClient.subscribeUser(result.user.id)
                    emoteRepository.setSevenTVEmotes(channel, result)
                }
        }.let { Log.i(TAG, "Loaded 7TV emotes for #$channel in $it ms") }
    }

    suspend fun loadGlobalFFZEmotes() = withContext(Dispatchers.IO) {
        if (VisibleThirdPartyEmotes.FFZ !in chatSettingsDataStore.current().visibleEmotes) {
            return@withContext
        }

        measureTimeMillis {
            ffzApiClient.getFFZGlobalEmotes()
                .getOrEmitFailure { DataLoadingStep.GlobalFFZEmotes }
                ?.let { emoteRepository.setFFZGlobalEmotes(it) }
        }.let { Log.i(TAG, "Loaded global FFZ emotes in $it ms") }
    }

    suspend fun loadGlobalBTTVEmotes() = withContext(Dispatchers.IO) {
        if (VisibleThirdPartyEmotes.BTTV !in chatSettingsDataStore.current().visibleEmotes) {
            return@withContext
        }

        measureTimeMillis {
            bttvApiClient.getBTTVGlobalEmotes()
                .getOrEmitFailure { DataLoadingStep.GlobalBTTVEmotes }
                ?.let { emoteRepository.setBTTVGlobalEmotes(it) }
        }.let { Log.i(TAG, "Loaded global BTTV emotes in $it ms") }
    }

    suspend fun loadGlobalSevenTVEmotes() = withContext(Dispatchers.IO) {
        if (VisibleThirdPartyEmotes.SevenTV !in chatSettingsDataStore.current().visibleEmotes) {
            return@withContext
        }

        measureTimeMillis {
            sevenTVApiClient.getSevenTVGlobalEmotes()
                .getOrEmitFailure { DataLoadingStep.GlobalSevenTVEmotes }
                ?.let { emoteRepository.setSevenTVGlobalEmotes(it) }
        }.let { Log.i(TAG, "Loaded global 7TV emotes in $it ms") }
    }

    private fun <T> Result<T>.getOrEmitFailure(step: () -> DataLoadingStep): T? = getOrElse { throwable ->
        Log.e(TAG, "Data request failed:", throwable)
        _dataLoadingFailures.update { it + DataLoadingFailure(step(), throwable) }
        null
    }

    companion object {
        private val TAG = DataRepository::class.java.simpleName
        private const val BADGES_SUNSET_MILLIS = 1685637000000L // 2023-06-01 16:30:00
    }
}
