package com.flxrs.dankchat.service

import android.util.Log
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.utils.extensions.measureTimeAndLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.measureTimeMillis

class DataRepository {
    private val emotes = mutableMapOf<String, MutableStateFlow<List<GenericEmote>>>()
    private val supibotCommands = mutableMapOf<String, MutableStateFlow<List<String>>>()

    private var loadedGlobalData = false
    private var loadedGlobalEmotes = false
    private var loadedGlobalBadges = false

    fun getEmotes(channel: String): StateFlow<List<GenericEmote>> = emotes.getOrPut(channel) { MutableStateFlow(emptyList()) }

    fun getSupibotCommands(channel: String): StateFlow<List<String>> = supibotCommands.getOrPut(channel) { MutableStateFlow(emptyList()) }

    suspend fun loadData(channels: List<String>, oAuth: String, id: Int, loadTwitchData: Boolean, loadSupibot: Boolean) = withContext(Dispatchers.IO) {
        if (oAuth.isNotBlank() && loadTwitchData) {
            launch { loadTwitchEmotes(oAuth, id) }
        }

        if (!loadedGlobalData) {
            loadedGlobalData = true
            launch { loadDankChatBadges() }
            if (loadSupibot) launch { loadSupibotCommands() }
        }

        channels.map { channel ->
            if (!emotes.contains(channel)) emotes[channel] = MutableStateFlow(emptyList())
            launch {
                TwitchApi.getUserIdFromName(oAuth, channel)?.let {
                    loadBadges(channel, it)
                    load3rdPartyEmotes(channel, it)
                }
                setEmotesForSuggestions(channel)
            }
        }.joinAll()
    }

    suspend fun reloadEmotes(channel: String, oAuth: String, id: Int) = withContext(Dispatchers.IO) {
        loadedGlobalEmotes = false
        launch {
            TwitchApi.getUserIdFromName(oAuth, channel)?.let {
                load3rdPartyEmotes(channel, it)
            }

            if (id != 0 && oAuth.isNotBlank()) {
                loadTwitchEmotes(oAuth, id)
            }
            setEmotesForSuggestions(channel)
        }

        launch { loadDankChatBadges() }
        launch { loadSupibotCommands() }
    }

    suspend fun uploadMedia(file: File): String? = TwitchApi.uploadMedia(file)

    fun clearSupibotCommands() = supibotCommands.forEach { it.value.value = emptyList() }.also { supibotCommands.clear() }

    suspend fun loadSupibotCommands() {
        measureTimeMillis {
            val channels = TwitchApi.getSupibotChannels()?.let { channels ->
                channels.data
                    .filter { it.isActive() }
                    .map { it.name }
            } ?: return@measureTimeMillis

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
            }
        }.let { Log.i(TAG, "Loaded Supibot commands in $it ms") }
    }

    private suspend fun loadBadges(channel: String, id: String) {
        if (!loadedGlobalBadges) {
            loadedGlobalBadges = true
            measureTimeAndLog(TAG, "global badges") {
                TwitchApi.getGlobalBadges()?.also { EmoteManager.setGlobalBadges(it) }
            }
        }
        measureTimeAndLog(TAG, "channel badges for #$id") {
            TwitchApi.getChannelBadges(id)?.also { EmoteManager.setChannelBadges(channel, it) }
        }
    }

    private suspend fun loadTwitchEmotes(oAuth: String, id: Int) {
        measureTimeAndLog(TAG, "twitch emotes for #$id") {
            TwitchApi.getUserEmotes(oAuth, id)?.also { EmoteManager.setTwitchEmotes(it) }
        }
    }

    private suspend fun load3rdPartyEmotes(channel: String, id: String) {
        measureTimeMillis {
            TwitchApi.getFFZChannelEmotes(id)?.let { EmoteManager.setFFZEmotes(channel, it) }
            TwitchApi.getBTTVChannelEmotes(id)?.let { EmoteManager.setBTTVEmotes(channel, it) }

            if (!loadedGlobalEmotes) {
                TwitchApi.getFFZGlobalEmotes()?.let { EmoteManager.setFFZGlobalEmotes(it) }
                TwitchApi.getBTTVGlobalEmotes()?.let { EmoteManager.setBTTVGlobalEmotes(it) }
                loadedGlobalEmotes = true
            }
        }.let { Log.i(TAG, "Loaded 3rd party emotes for #$channel in $it ms") }
    }

    private suspend fun loadDankChatBadges() {
        measureTimeMillis {
            TwitchApi.getDankChatBadges()?.let { EmoteManager.setDankChatBadges(it) }
        }.let { Log.i(TAG, "Loaded DankChat badges in $it ms") }
    }

    private suspend fun setEmotesForSuggestions(channel: String) = withContext(Dispatchers.Default) {
        if (!emotes.contains(channel)) {
            emotes[channel] = MutableStateFlow(emptyList())
        }

        emotes[channel]?.value = EmoteManager.getEmotes(channel)
    }

    companion object {
        private val TAG = DataRepository::class.java.simpleName
    }
}