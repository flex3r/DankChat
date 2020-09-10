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

class DataRepository {
    private val emotes = mutableMapOf<String, MutableStateFlow<List<GenericEmote>>>()
    private val supibotCommands = mutableMapOf<String, MutableStateFlow<List<String>>>()
    private var loadedGlobalEmotes = false

    fun getEmotes(channel: String): StateFlow<List<GenericEmote>> = emotes.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun getSupibotCommands(channel: String): StateFlow<List<String>> = supibotCommands.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun clearSupibotCommands() = supibotCommands.forEach { it.value.value = emptyList() }.also { supibotCommands.clear() }

    suspend fun loadChannelData(channel: String, oAuth: String, forceReload: Boolean = false) {
        if (!emotes.contains(channel)) emotes[channel] = MutableStateFlow(emptyList())
        TwitchApi.getUserIdFromName(oAuth, channel)?.let {
            loadChannelBadges(channel, it)
            load3rdPartyEmotes(channel, it, forceReload)
        }
    }

    suspend fun uploadMedia(file: File): String? = TwitchApi.uploadMedia(file)

    suspend fun loadSupibotCommands() {
        measureTimeMillis {
            val channels = TwitchApi.getSupibotChannels()?.let { channels ->
                channels.data
                    .filter { it.isActive() }
                    .map { it.name }
            } ?: return

            TwitchApi.getSupibotCommands()?.let { commands ->
                val commandsWithAliases = commands.data.map {
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
            TwitchApi.getGlobalBadges()?.also { EmoteManager.setGlobalBadges(it) }
        }
    }

    suspend fun loadTwitchEmotes(oAuth: String, id: String) {
        if (oAuth.isNotBlank()) {
            measureTimeAndLog(TAG, "twitch emotes for #$id") {
                TwitchApi.getUserEmotes(oAuth, id)?.also { EmoteManager.setTwitchEmotes(it) }
            }
        }
    }

    suspend fun loadDankChatBadges() {
        measureTimeMillis {
            TwitchApi.getDankChatBadges()?.let { EmoteManager.setDankChatBadges(it) }
        }.let { Log.i(TAG, "Loaded DankChat badges in $it ms") }
    }

    suspend fun setEmotesForSuggestions(channel: String) {
        if (!emotes.contains(channel)) {
            emotes[channel] = MutableStateFlow(emptyList())
        }

        emotes[channel]?.value = EmoteManager.getEmotes(channel)
    }

    private suspend fun loadChannelBadges(channel: String, id: String) {
        measureTimeAndLog(TAG, "channel badges for #$id") {
            TwitchApi.getChannelBadges(id)?.also { EmoteManager.setChannelBadges(channel, it) }
        }
    }

    private suspend fun load3rdPartyEmotes(channel: String, id: String, forceReload: Boolean) {
        measureTimeMillis {
            TwitchApi.getFFZChannelEmotes(id)?.let { EmoteManager.setFFZEmotes(channel, it) }
            TwitchApi.getBTTVChannelEmotes(id)?.let { EmoteManager.setBTTVEmotes(channel, it) }

            if (forceReload || !loadedGlobalEmotes) {
                TwitchApi.getFFZGlobalEmotes()?.let { EmoteManager.setFFZGlobalEmotes(it) }
                TwitchApi.getBTTVGlobalEmotes()?.let { EmoteManager.setBTTVGlobalEmotes(it) }
                loadedGlobalEmotes = true
            }
        }.let { Log.i(TAG, "Loaded 3rd party emotes for #$channel in $it ms") }
    }

    companion object {
        private val TAG = DataRepository::class.java.simpleName
    }
}