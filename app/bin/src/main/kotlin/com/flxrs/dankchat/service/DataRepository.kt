package com.flxrs.dankchat.service

import android.util.Log
import com.flxrs.dankchat.service.api.ApiManager
import com.flxrs.dankchat.service.api.dto.HelixUserDto
import com.flxrs.dankchat.service.api.dto.UserFollowsDto
import com.flxrs.dankchat.service.twitch.badge.toBadgeSets
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.service.twitch.emote.ThirdPartyEmoteType
import com.flxrs.dankchat.utils.extensions.measureTimeAndLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.system.measureTimeMillis

class DataRepository(private val apiManager: ApiManager, private val emoteManager: EmoteManager) {
    private val emotes = mutableMapOf<String, MutableStateFlow<List<GenericEmote>>>()
    private val supibotCommands = mutableMapOf<String, MutableStateFlow<List<String>>>()
    private var loadedGlobalEmotes = false

    fun getEmotes(channel: String): StateFlow<List<GenericEmote>> = emotes.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun getSupibotCommands(channel: String): StateFlow<List<String>> = supibotCommands.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun clearSupibotCommands() = supibotCommands.forEach { it.value.value = emptyList() }.also { supibotCommands.clear() }

    suspend fun loadChannelData(channel: String, oAuth: String, channelId: String? = null, loadThirdPartyData: Set<ThirdPartyEmoteType>, forceReload: Boolean = false) {
        emotes.putIfAbsent(channel, MutableStateFlow(emptyList()))
        val id = channelId ?: apiManager.getUserIdByName(oAuth, channel) ?: return
        loadChannelBadges(oAuth, channel, id)
        load3rdPartyEmotes(channel, id, loadThirdPartyData, forceReload)
    }

    suspend fun getUser(oAuth: String, userId: String): HelixUserDto? = apiManager.getUser(oAuth, userId)
    suspend fun getUserIdByName(oAuth: String, name: String): String? = apiManager.getUserIdByName(oAuth, name)
    suspend fun getUserFollows(oAuth: String, fromId: String, toId: String): UserFollowsDto? = apiManager.getUsersFollows(oAuth, fromId, toId)
    suspend fun followUser(oAuth: String, fromId: String, toId: String): Boolean = apiManager.followUser(oAuth, fromId, toId)
    suspend fun unfollowUser(oAuth: String, fromId: String, toId: String): Boolean = apiManager.unfollowUser(oAuth, fromId, toId)
    suspend fun blockUser(oAuth: String, targetUserId: String): Boolean = apiManager.blockUser(oAuth, targetUserId)
    suspend fun unblockUser(oAuth: String, targetUserId: String): Boolean = apiManager.unblockUser(oAuth, targetUserId)

    suspend fun uploadMedia(file: File): String? = apiManager.uploadMedia(file)

    suspend fun loadSupibotCommands() {
        measureTimeMillis {
            val channels = apiManager.getSupibotChannels()?.let { (data) ->
                data
                    .filter { it.isActive() }
                    .map { it.name }
            } ?: return

            apiManager.getSupibotCommands()?.let { (data) ->
                val commandsWithAliases = data.map {
                    listOf(it.name) + it.aliases
                }.flatten()

                channels.forEach {
                    supibotCommands.putIfAbsent(it, MutableStateFlow(emptyList()))
                    supibotCommands[it]?.value = commandsWithAliases
                }
            } ?: return
        }.let { Log.i(TAG, "Loaded Supibot commands in $it ms") }
    }

    suspend fun loadGlobalBadges(oAuth: String) = withContext(Dispatchers.Default) {
        measureTimeAndLog(TAG, "global badges") {
            val badges = when {
                oAuth.isBlank() -> apiManager.getGlobalBadgesFallback()?.toBadgeSets()
                else            -> apiManager.getGlobalBadges(oAuth)?.toBadgeSets() ?: apiManager.getGlobalBadgesFallback()?.toBadgeSets()
            }
            badges?.let { emoteManager.setGlobalBadges(it) }
        }
    }

    suspend fun loadTwitchEmotes(oAuth: String, id: String) {
        if (oAuth.isNotBlank()) {
            measureTimeAndLog(TAG, "twitch emotes for #$id") {
                apiManager.getUserEmotes(oAuth, id)?.also { emoteManager.setTwitchEmotes(oAuth, it) }
            }
        }
    }

    suspend fun loadDankChatBadges() {
        measureTimeMillis {
            apiManager.getDankChatBadges()?.let { emoteManager.setDankChatBadges(it) }
        }.let { Log.i(TAG, "Loaded DankChat badges in $it ms") }
    }

    suspend fun setEmotesForSuggestions(channel: String) {
        emotes.putIfAbsent(channel, MutableStateFlow(emptyList()))
        emotes[channel]?.value = emoteManager.getEmotes(channel)
    }

    suspend fun loadUserStateEmotes(userStateEmoteSets: List<String>) {
        emoteManager.loadUserStateEmotes(userStateEmoteSets)
    }

    private suspend fun loadChannelBadges(oAuth: String, channel: String, id: String) = withContext(Dispatchers.Default) {
        measureTimeAndLog(TAG, "channel badges for #$id") {
            val badges = when {
                oAuth.isBlank() -> apiManager.getChannelBadgesFallback(id)?.toBadgeSets()
                else            -> apiManager.getChannelBadges(oAuth, id)?.toBadgeSets() ?: apiManager.getChannelBadgesFallback(id)?.toBadgeSets()
            }
            badges?.let { emoteManager.setChannelBadges(channel, it) }
        }
    }

    private suspend fun load3rdPartyEmotes(channel: String, id: String, loadThirdPartyData: Set<ThirdPartyEmoteType>, forceReload: Boolean) = coroutineScope {
        measureTimeMillis {
            listOfNotNull(
                launchWhenTypeOrNull(ThirdPartyEmoteType.FrankerFaceZ, loadThirdPartyData) {
                    apiManager.getFFZChannelEmotes(id)?.let { emoteManager.setFFZEmotes(channel, it) }
                } ?: emoteManager.clearFFZEmotes(),
                launchWhenTypeOrNull(ThirdPartyEmoteType.BetterTTV, loadThirdPartyData) {
                    apiManager.getBTTVChannelEmotes(id)?.let { emoteManager.setBTTVEmotes(channel, it) }
                } ?: emoteManager.clearBTTVEmotes(),
                launchWhenTypeOrNull(ThirdPartyEmoteType.SevenTV, loadThirdPartyData) {
                    apiManager.getSevenTVChannelEmotes(channel)?.let { emoteManager.setSevenTVEmotes(channel, it) }
                } ?: emoteManager.clearSevenTVEmotes(),
            ).joinAll()

            if (forceReload || !loadedGlobalEmotes) {
                listOfNotNull(
                    launchWhenTypeOrNull(ThirdPartyEmoteType.FrankerFaceZ, loadThirdPartyData) {
                        apiManager.getFFZGlobalEmotes()?.let { emoteManager.setFFZGlobalEmotes(it) }
                    },
                    launchWhenTypeOrNull(ThirdPartyEmoteType.BetterTTV, loadThirdPartyData) {
                        apiManager.getBTTVGlobalEmotes()?.let { emoteManager.setBTTVGlobalEmotes(it) }
                    },
                    launchWhenTypeOrNull(ThirdPartyEmoteType.SevenTV, loadThirdPartyData) {
                        apiManager.getSevenTVGlobalEmotes()?.let { emoteManager.setSevenTVGlobalEmotes(it) }
                    },
                ).joinAll()
            }
            loadedGlobalEmotes = true
        }.let { Log.i(TAG, "Loaded 3rd party emotes for #$channel in $it ms") }
    }

    private fun CoroutineScope.launchWhenTypeOrNull(type: ThirdPartyEmoteType, loadThirdPartyData: Set<ThirdPartyEmoteType>, block: suspend () -> Unit): Job? = when (type) {
        in loadThirdPartyData -> launch { block() }
        else                  -> null
    }

    companion object {
        private val TAG = DataRepository::class.java.simpleName
    }
}