package com.flxrs.dankchat.data.repo.data

import android.util.Log
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
import com.flxrs.dankchat.data.api.upload.UploadClient
import com.flxrs.dankchat.data.repo.EmoteRepository
import com.flxrs.dankchat.data.repo.RecentUploadsRepository
import com.flxrs.dankchat.data.twitch.badge.toBadgeSets
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.data.twitch.emote.ThirdPartyEmoteType
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.measureTimeAndLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class DataRepository @Inject constructor(
    private val helixApiClient: HelixApiClient,
    private val dankChatApiClient: DankChatApiClient,
    private val badgesApiClient: BadgesApiClient,
    private val ffzApiClient: FFZApiClient,
    private val bttvApiClient: BTTVApiClient,
    private val sevenTVApiClient: SevenTVApiClient,
    private val uploadClient: UploadClient,
    private val emoteRepository: EmoteRepository,
    private val recentUploadsRepository: RecentUploadsRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
) {
    private val emotes = ConcurrentHashMap<UserName, MutableStateFlow<List<GenericEmote>>>()
    private val _dataLoadingFailures = MutableStateFlow(emptySet<DataLoadingFailure>())

    private val serviceEventChannel = Channel<ServiceEvent>(Channel.BUFFERED)
    val serviceEvents = serviceEventChannel.receiveAsFlow()

    val dataLoadingFailures = _dataLoadingFailures.asStateFlow()

    fun clearDataLoadingFailures() = _dataLoadingFailures.update { emptySet() }

    fun getEmotes(channel: UserName): StateFlow<List<GenericEmote>> = emotes.getOrPut(channel) { MutableStateFlow(emptyList()) }

    suspend fun getUser(userId: UserId): UserDto? = helixApiClient.getUser(userId).getOrNull()
    suspend fun getUserIdByName(name: UserName): UserId? = helixApiClient.getUserIdByName(name).getOrNull()
    suspend fun getUserFollows(fromId: UserId, toId: UserId): UserFollowsDto? = helixApiClient.getUsersFollows(fromId, toId).getOrNull()
    suspend fun getStreams(channels: List<UserName>): List<StreamDto>? = helixApiClient.getStreams(channels).getOrNull()

    suspend fun uploadMedia(file: File): Result<String> = uploadClient.uploadMedia(file).mapCatching {
        recentUploadsRepository.addUpload(it)
        it.imageLink
    }

    suspend fun loadGlobalBadges() = withContext(Dispatchers.Default) {
        measureTimeAndLog(TAG, "global badges") {
            val badges = badgesApiClient.getGlobalBadges()
                .getOrEmitFailure { DataLoadingStep.GlobalBadges }
                ?.toBadgeSets()
            badges?.let { emoteRepository.setGlobalBadges(it) }
        }
    }

    suspend fun loadDankChatBadges() = withContext(Dispatchers.Default) {
        measureTimeMillis {
            dankChatApiClient.getDankChatBadges()
                .getOrEmitFailure { DataLoadingStep.DankChatBadges }
                ?.let { emoteRepository.setDankChatBadges(it) }
        }.let { Log.i(TAG, "Loaded DankChat badges in $it ms") }
    }

    // TODO refactor to flow/observe pattern
    suspend fun setEmotesForSuggestions(channel: UserName) {
        emotes.putIfAbsent(channel, MutableStateFlow(emptyList()))
        emotes[channel]?.value = emoteRepository.getEmotes(channel)
    }

    suspend fun loadUserStateEmotes(globalEmoteSetIds: List<String>, followerEmoteSetIds: Map<UserName, List<String>>) {
        emoteRepository.loadUserStateEmotes(globalEmoteSetIds, followerEmoteSetIds)
    }

    suspend fun sendShutdownCommand() {
        serviceEventChannel.send(ServiceEvent.Shutdown)
    }

    suspend fun loadChannelBadges(channel: UserName, id: UserId) = withContext(Dispatchers.Default) {
        measureTimeAndLog(TAG, "channel badges for #$id") {
            val badges = badgesApiClient.getChannelBadges(id)
                .getOrEmitFailure { DataLoadingStep.ChannelBadges(channel, id) }
                ?.toBadgeSets()
            badges?.let { emoteRepository.setChannelBadges(channel, it) }
        }
    }

    suspend fun loadChannelFFZEmotes(channel: UserName, channelId: UserId) = withContext(Dispatchers.Default) {
        if (ThirdPartyEmoteType.FrankerFaceZ !in dankChatPreferenceStore.visibleThirdPartyEmotes) {
            return@withContext
        }

        measureTimeMillis {
            ffzApiClient.getFFZChannelEmotes(channelId)
                .getOrEmitFailure { DataLoadingStep.ChannelFFZEmotes(channel, channelId) }
                ?.let { emoteRepository.setFFZEmotes(channel, it) }
        }.let { Log.i(TAG, "Loaded FFZ emotes for #$channel in $it ms") }
    }

    suspend fun loadChannelBTTVEmotes(channel: UserName, channelId: UserId) = withContext(Dispatchers.Default) {
        if (ThirdPartyEmoteType.BetterTTV !in dankChatPreferenceStore.visibleThirdPartyEmotes) {
            return@withContext
        }

        measureTimeMillis {
            bttvApiClient.getBTTVChannelEmotes(channelId)
                .getOrEmitFailure { DataLoadingStep.ChannelBTTVEmotes(channel, channelId) }
                ?.let { emoteRepository.setBTTVEmotes(channel, it) }
        }.let { Log.i(TAG, "Loaded BTTV emotes for #$channel in $it ms") }
    }

    suspend fun loadChannelSevenTVEmotes(channel: UserName, channelId: UserId) = withContext(Dispatchers.Default) {
        if (ThirdPartyEmoteType.SevenTV !in dankChatPreferenceStore.visibleThirdPartyEmotes) {
            return@withContext
        }

        measureTimeMillis {
            sevenTVApiClient.getSevenTVChannelEmotes(channelId)
                .getOrEmitFailure { DataLoadingStep.ChannelSevenTVEmotes(channel, channelId) }
                ?.let { emoteRepository.setSevenTVEmotes(channel, it) }
        }.let { Log.i(TAG, "Loaded 7TV emotes for #$channel in $it ms") }
    }

    suspend fun loadGlobalFFZEmotes() = withContext(Dispatchers.Default) {
        if (ThirdPartyEmoteType.FrankerFaceZ !in dankChatPreferenceStore.visibleThirdPartyEmotes) {
            return@withContext
        }

        measureTimeMillis {
            ffzApiClient.getFFZGlobalEmotes()
                .getOrEmitFailure { DataLoadingStep.GlobalFFZEmotes }
                ?.let { emoteRepository.setFFZGlobalEmotes(it) }
        }.let { Log.i(TAG, "Loaded global FFZ emotes in $it ms") }
    }

    suspend fun loadGlobalBTTVEmotes() = withContext(Dispatchers.Default) {
        if (ThirdPartyEmoteType.BetterTTV !in dankChatPreferenceStore.visibleThirdPartyEmotes) {
            return@withContext
        }

        measureTimeMillis {
            bttvApiClient.getBTTVGlobalEmotes()
                .getOrEmitFailure { DataLoadingStep.GlobalBTTVEmotes }
                ?.let { emoteRepository.setBTTVGlobalEmotes(it) }
        }.let { Log.i(TAG, "Loaded global BTTV emotes in $it ms") }
    }

    suspend fun loadGlobalSevenTVEmotes() = withContext(Dispatchers.Default) {
        if (ThirdPartyEmoteType.SevenTV !in dankChatPreferenceStore.visibleThirdPartyEmotes) {
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
    }
}