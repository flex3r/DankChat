package com.flxrs.dankchat.service

import android.util.Log
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.utils.extensions.measureTimeAndLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.system.measureTimeMillis

class DataRepository(private val twitchApi: TwitchApi, private val emoteManager: EmoteManager) {
    private val emotes = mutableMapOf<String, MutableStateFlow<List<GenericEmote>>>()
    private val supibotCommands = mutableMapOf<String, MutableStateFlow<List<String>>>()
    private var loadedGlobalEmotes = false

    fun getEmotes(channel: String): StateFlow<List<GenericEmote>> = emotes.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun getSupibotCommands(channel: String): StateFlow<List<String>> = supibotCommands.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun clearSupibotCommands() = supibotCommands.forEach { it.value.value = emptyList() }.also { supibotCommands.clear() }

    suspend fun loadChannelData(channel: String, oAuth: String, forceReload: Boolean = false) {
        if (!emotes.contains(channel)) emotes[channel] = MutableStateFlow(emptyList())
        twitchApi.getUserIdFromName(oAuth, channel)?.let {
            loadChannelBadges(channel, it)
            load3rdPartyEmotes(channel, it, forceReload)
        }
    }

    suspend fun uploadMedia(file: File): String? = twitchApi.uploadMedia(file)

    suspend fun loadSupibotCommands() {
        measureTimeMillis {
            val channels = twitchApi.getSupibotChannels()?.let { (data) ->
                data
                    .filter { it.isActive() }
                    .map { it.name }
            } ?: return

            twitchApi.getSupibotCommands()?.let { (data) ->
                val commandsWithAliases = data.map {
                    listOf(it.name) + it.aliases
                }.flatten()

                channels.forEach {
                    if (!supibotCommands.contains(it)) {
                        supibotCommands[it] = MutableStateFlow(emptyList())
                    }

                    supibotCommands[it]?.value = commandsWithAliases
                }
            } ?: return
        }.let { Log.i(TAG, "Loaded Supibot commands in $it ms") }
    }

    suspend fun loadGlobalBadges() {
        measureTimeAndLog(TAG, "global badges") {
            twitchApi.getGlobalBadges()?.also { emoteManager.setGlobalBadges(it) }
        }
    }

    suspend fun loadTwitchEmotes(oAuth: String, id: String) {
        if (oAuth.isNotBlank()) {
            measureTimeAndLog(TAG, "twitch emotes for #$id") {
                twitchApi.getUserEmotes(oAuth, id)?.also { emoteManager.setTwitchEmotes(it) }
            }
        }
    }

    suspend fun loadDankChatBadges() {
        measureTimeMillis {
            twitchApi.getDankChatBadges()?.let { emoteManager.setDankChatBadges(it) }
        }.let { Log.i(TAG, "Loaded DankChat badges in $it ms") }
    }

    suspend fun setEmotesForSuggestions(channel: String) {
        if (!emotes.contains(channel)) {
            emotes[channel] = MutableStateFlow(emptyList())
        }

        emotes[channel]?.value = emoteManager.getEmotes(channel)
    }

    private suspend fun loadChannelBadges(channel: String, id: String) {
        measureTimeAndLog(TAG, "channel badges for #$id") {
            twitchApi.getChannelBadges(id)?.also { emoteManager.setChannelBadges(channel, it) }
        }
    }

    private suspend fun load3rdPartyEmotes(channel: String, id: String, forceReload: Boolean) {
        measureTimeMillis {
            twitchApi.getFFZChannelEmotes(id)?.let { emoteManager.setFFZEmotes(channel, it) }
            twitchApi.getBTTVChannelEmotes(id)?.let { emoteManager.setBTTVEmotes(channel, it) }

            if (forceReload || !loadedGlobalEmotes) {
                twitchApi.getFFZGlobalEmotes()?.let { emoteManager.setFFZGlobalEmotes(it) }
                twitchApi.getBTTVGlobalEmotes()?.let { emoteManager.setBTTVGlobalEmotes(it) }
                loadedGlobalEmotes = true
            }
        }.let { Log.i(TAG, "Loaded 3rd party emotes for #$channel in $it ms") }
    }

    companion object {
        private val TAG = DataRepository::class.java.simpleName
    }
}