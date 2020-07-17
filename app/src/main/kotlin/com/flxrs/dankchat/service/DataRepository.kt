package com.flxrs.dankchat.service

import android.util.Log
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.utils.extensions.measureTimeAndLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.IllegalStateException
import kotlin.system.measureTimeMillis

class DataRepository {
    private val emotes = mutableMapOf<String, MutableStateFlow<List<GenericEmote>>>()

    private var loadedGlobalBadges = false
    private var loadedGlobalEmotes = false
    private var loadedTwitchEmotes = false

    fun getEmotes(channel: String): StateFlow<List<GenericEmote>> = emotes.getOrPut(channel) { MutableStateFlow(emptyList()) }

    suspend fun loadData(channels: List<String>, oAuth: String, id: Int, loadTwitchData: Boolean) = withContext(Dispatchers.IO) {
        if (oAuth.isNotBlank() && loadTwitchData) {
            loadedTwitchEmotes = false
            loadTwitchEmotes(oAuth, id)
        }
        channels.map { channel ->
            if (!emotes.contains(channel)) emotes[channel] = MutableStateFlow(emptyList())
            async {
                TwitchApi.getUserIdFromName(oAuth, channel)?.let {
                    loadBadges(channel, it)
                    load3rdPartyEmotes(channel, it)
                }
                setEmotesForSuggestions(channel)
            }
        }.awaitAll()
    }

    suspend fun reloadEmotes(channel: String, oAuth: String, id: Int) = withContext(Dispatchers.IO) {
        loadedGlobalEmotes = false
        loadedTwitchEmotes = false
        TwitchApi.getUserIdFromName(oAuth, channel)?.let {
            load3rdPartyEmotes(channel, it)
        }

        if (id != 0 && oAuth.isNotBlank() && oAuth.startsWith("oauth:")) {
            loadTwitchEmotes(oAuth.substringAfter("oauth:"), id)
        }

        setEmotesForSuggestions(channel)
    }

    suspend fun uploadMedia(file: File): String? = withContext(Dispatchers.IO) {
        TwitchApi.uploadMedia(file).also { file.delete() }
    }

    private suspend fun loadBadges(channel: String, id: String) = withContext(Dispatchers.Default) {
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

    private suspend fun loadTwitchEmotes(oAuth: String, id: Int) = withContext(Dispatchers.Default) {
        if (!loadedTwitchEmotes) {
            loadedTwitchEmotes = true
            measureTimeAndLog(TAG, "twitch emotes for #$id") {
                TwitchApi.getUserEmotes(oAuth, id)?.also { EmoteManager.setTwitchEmotes(it) }
            }
        }
    }

    private suspend fun load3rdPartyEmotes(channel: String, id: String) = withContext(Dispatchers.IO) {
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