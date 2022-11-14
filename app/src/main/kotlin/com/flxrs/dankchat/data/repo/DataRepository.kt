package com.flxrs.dankchat.data.repo

import android.util.Log
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.data.api.dto.HelixUserDto
import com.flxrs.dankchat.data.api.dto.UserFollowsDto
import com.flxrs.dankchat.data.twitch.badge.toBadgeSets
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.data.twitch.emote.ThirdPartyEmoteType
import com.flxrs.dankchat.utils.extensions.measureTimeAndLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class DataRepository @Inject constructor(
    private val apiManager: ApiManager,
    private val emoteRepository: EmoteRepository,
    private val recentUploadsRepository: RecentUploadsRepository,
) {
    private val emotes = ConcurrentHashMap<String, MutableStateFlow<List<GenericEmote>>>()

    sealed class ServiceEvent {
        object Shutdown : ServiceEvent()
    }

    private val commandsChannel = Channel<ServiceEvent>(Channel.BUFFERED)
    val commands = commandsChannel.receiveAsFlow()

    fun getEmotes(channel: String): StateFlow<List<GenericEmote>> = emotes.getOrPut(channel) { MutableStateFlow(emptyList()) }

    suspend fun loadChannelData(channel: String, channelId: String? = null, loadThirdPartyData: Set<ThirdPartyEmoteType>) = withContext(Dispatchers.Default) {
        emotes.putIfAbsent(channel, MutableStateFlow(emptyList()))
        val id = channelId ?: apiManager.getUserIdByName(channel) ?: return@withContext
        launch { loadChannelBadges(channel, id) }
        launch {
            measureTimeMillis {
                load3rdPartyChannelEmotes(channel, id, loadThirdPartyData)
            }.let { Log.i(TAG, "Loaded 3rd party emotes for #$channel in $it ms") }
        }
    }

    suspend fun loadGlobalData(loadThirdPartyData: Set<ThirdPartyEmoteType>) = withContext(Dispatchers.Default) {
        measureTimeMillis {
            load3rdPartyGlobalEmotes(loadThirdPartyData)
        }.let { Log.i(TAG, "Loaded 3rd party global emotes in $it ms") }
    }

    suspend fun getUser(userId: String): HelixUserDto? = apiManager.getUser(userId)
    suspend fun getUserIdByName(name: String): String? = apiManager.getUserIdByName(name)
    suspend fun getUserFollows(fromId: String, toId: String): UserFollowsDto? = apiManager.getUsersFollows(fromId, toId)

    suspend fun uploadMedia(file: File): Result<String> {
        val uploadResult = apiManager.uploadMedia(file)
        return uploadResult.mapCatching {
            recentUploadsRepository.addUpload(it)
            it.imageLink
        }
    }

    suspend fun loadGlobalBadges() = withContext(Dispatchers.Default) {
        measureTimeAndLog(TAG, "global badges") {
            val badges = apiManager.getGlobalBadges()?.toBadgeSets()
            badges?.let { emoteRepository.setGlobalBadges(it) }
        }
    }

    suspend fun loadDankChatBadges() = withContext(Dispatchers.Default) {
        measureTimeMillis {
            apiManager.getDankChatBadges()?.let { emoteRepository.setDankChatBadges(it) }
        }.let { Log.i(TAG, "Loaded DankChat badges in $it ms") }
    }

    // TODO refactor to flow/observe pattern
    suspend fun setEmotesForSuggestions(channel: String) {
        emotes.putIfAbsent(channel, MutableStateFlow(emptyList()))
        emotes[channel]?.value = emoteRepository.getEmotes(channel)
    }

    suspend fun loadUserStateEmotes(globalEmoteSetIds: List<String>, followerEmoteSetIds: Map<String, List<String>>) {
        emoteRepository.loadUserStateEmotes(globalEmoteSetIds, followerEmoteSetIds)
    }

    suspend fun sendShutdownCommand() {
        commandsChannel.send(ServiceEvent.Shutdown)
    }

    private suspend fun loadChannelBadges(channel: String, id: String) {
        measureTimeAndLog(TAG, "channel badges for #$id") {
            val badges = apiManager.getChannelBadges(id)?.toBadgeSets()
            badges?.let { emoteRepository.setChannelBadges(channel, it) }
        }
    }

    private suspend fun load3rdPartyChannelEmotes(channel: String, id: String, loadThirdPartyData: Set<ThirdPartyEmoteType>) = coroutineScope {
        launchWhenEnabled(ThirdPartyEmoteType.FrankerFaceZ, loadThirdPartyData) {
            apiManager.getFFZChannelEmotes(id)?.let { emoteRepository.setFFZEmotes(channel, it) }
        } ?: emoteRepository.clearFFZEmotes()
        launchWhenEnabled(ThirdPartyEmoteType.BetterTTV, loadThirdPartyData) {
            apiManager.getBTTVChannelEmotes(id)?.let { emoteRepository.setBTTVEmotes(channel, it) }
        } ?: emoteRepository.clearBTTVEmotes()
        launchWhenEnabled(ThirdPartyEmoteType.SevenTV, loadThirdPartyData) {
            apiManager.getSevenTVChannelEmotes(id)?.let { emoteRepository.setSevenTVEmotes(channel, it) }
        } ?: emoteRepository.clearSevenTVEmotes()
    }

    private suspend fun load3rdPartyGlobalEmotes(loadThirdPartyData: Set<ThirdPartyEmoteType>) = coroutineScope {
        launchWhenEnabled(ThirdPartyEmoteType.FrankerFaceZ, loadThirdPartyData) {
            apiManager.getFFZGlobalEmotes()?.let { emoteRepository.setFFZGlobalEmotes(it) }
        }
        launchWhenEnabled(ThirdPartyEmoteType.BetterTTV, loadThirdPartyData) {
            apiManager.getBTTVGlobalEmotes()?.let { emoteRepository.setBTTVGlobalEmotes(it) }
        }
        launchWhenEnabled(ThirdPartyEmoteType.SevenTV, loadThirdPartyData) {
            apiManager.getSevenTVGlobalEmotes()?.let { emoteRepository.setSevenTVGlobalEmotes(it) }
        }
    }

    private fun CoroutineScope.launchWhenEnabled(type: ThirdPartyEmoteType, loadThirdPartyData: Set<ThirdPartyEmoteType>, block: suspend () -> Unit): Job? = when (type) {
        in loadThirdPartyData -> launch { block() }
        else                  -> null
    }

    companion object {
        private val TAG = DataRepository::class.java.simpleName
    }
}