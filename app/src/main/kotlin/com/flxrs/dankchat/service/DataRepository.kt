package com.flxrs.dankchat.service

import android.util.Log
import com.flxrs.dankchat.service.api.ApiManager
import com.flxrs.dankchat.service.api.dto.HelixUserDto
import com.flxrs.dankchat.service.api.dto.UserFollowsDto
import com.flxrs.dankchat.service.twitch.badge.toBadgeSets
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.utils.extensions.measureTimeAndLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.measureTimeMillis

class DataRepository(private val apiManager: ApiManager, private val emoteManager: EmoteManager) {
    private val emotes = mutableMapOf<String, MutableStateFlow<List<GenericEmote>>>()
    private val supibotCommands = mutableMapOf<String, MutableStateFlow<List<String>>>()
    private var loadedGlobalEmotes = false

    fun getEmotes(channel: String): StateFlow<List<GenericEmote>> = emotes.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun getSupibotCommands(channel: String): StateFlow<List<String>> = supibotCommands.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun clearSupibotCommands() = supibotCommands.forEach { it.value.value = emptyList() }.also { supibotCommands.clear() }

    suspend fun loadChannelData(channel: String, oAuth: String, channelId: String? = null, forceReload: Boolean = false) {
        emotes.putIfAbsent(channel, MutableStateFlow(emptyList()))
        val id = channelId ?: apiManager.getUserIdByName(oAuth, channel) ?: return
        loadChannelBadges(oAuth, channel, id)
        load3rdPartyEmotes(channel, id, forceReload)
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
                apiManager.getUserEmotes(oAuth, id)?.also { emoteManager.setTwitchEmotes(it) }
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

    suspend fun filterAndLoadUserStateEmotes(userStateEmoteSets: List<String>) {
        emoteManager.filterAndLoadUserStateEmotes(userStateEmoteSets)
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

    private suspend fun load3rdPartyEmotes(channel: String, id: String, forceReload: Boolean) {
        measureTimeMillis {
            apiManager.getFFZChannelEmotes(id)?.let { emoteManager.setFFZEmotes(channel, it) }
            apiManager.getBTTVChannelEmotes(id)?.let { emoteManager.setBTTVEmotes(channel, it) }

            if (forceReload || !loadedGlobalEmotes) {
                apiManager.getFFZGlobalEmotes()?.let { emoteManager.setFFZGlobalEmotes(it) }
                apiManager.getBTTVGlobalEmotes()?.let { emoteManager.setBTTVGlobalEmotes(it) }
                loadedGlobalEmotes = true
            }
        }.let { Log.i(TAG, "Loaded 3rd party emotes for #$channel in $it ms") }
    }

    companion object {
        private val TAG = DataRepository::class.java.simpleName
    }
}