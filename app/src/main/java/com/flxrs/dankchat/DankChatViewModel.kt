package com.flxrs.dankchat

import android.util.Log
import androidx.lifecycle.*
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.chat.menu.EmoteMenuTab
import com.flxrs.dankchat.chat.suggestion.Suggestion
import com.flxrs.dankchat.service.TwitchRepository
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.twitch.connection.ConnectionState
import com.flxrs.dankchat.service.twitch.emote.EmoteType
import com.flxrs.dankchat.utils.extensions.timer
import com.flxrs.dankchat.utils.extensions.toEmoteItems
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class DankChatViewModel(private val twitchRepository: TwitchRepository) : ViewModel() {

    private var fetchJob: Job? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, Log.getStackTraceString(t))
    }

    val activeChannel = MutableLiveData<String>()
    val channels = MutableLiveData<List<String>>(emptyList())

    private val streamInfoEnabled = MutableLiveData(true)
    private val roomStateEnabled = MutableLiveData(true)
    private val streamData: MutableLiveData<Map<String, String>> = MutableLiveData()
    private val roomState = activeChannel.switchMap { twitchRepository.getRoomState(it) }
    private val emotes = activeChannel.switchMap { twitchRepository.getEmotes(it) }
    private val users = activeChannel.switchMap { twitchRepository.getUsers(it) }
    private val currentStreamInformation = MediatorLiveData<String>().apply {
        addSource(activeChannel) { value = streamData.value?.get(it) ?: "" }
        addSource(streamData) {
            activeChannel.value?.let { channel ->
                value = it[channel] ?: ""
            }
        }
    }
    private val emoteSuggestions = emotes.switchMap { emotes ->
        liveData(Dispatchers.Default) {
            emit(emotes.distinctBy { it.code }.map { Suggestion.EmoteSuggestion(it) })
        }
    }
    private val userSuggestions = users.switchMap { users ->
        liveData(Dispatchers.Default) {
            emit(users.snapshot().keys.map { Suggestion.UserSuggestion(it) })
        }
    }

    var started = false
    val inputEnabled = MutableLiveData(true)
    val appbarEnabled = MutableLiveData(true)
    val shouldShowViewPager = MediatorLiveData<Boolean>().apply {
        addSource(channels) { value = it.isNotEmpty() }
    }
    val shouldShowInput = MediatorLiveData<Boolean>().apply {
        addSource(inputEnabled) { value = it && shouldShowViewPager.value ?: false }
        addSource(shouldShowViewPager) { value = it && inputEnabled.value ?: true }
    }
    val imageUploadedEvent = twitchRepository.imageUploadedEvent
    val showUploadProgress = MutableLiveData(false)
    val connectionState = activeChannel.switchMap { twitchRepository.getConnectionState(it) }
    val canType = connectionState.map { it == ConnectionState.CONNECTED }
    val bottomText = MediatorLiveData<String>().apply {
        addSource(roomStateEnabled) { value = buildBottomText() }
        addSource(streamInfoEnabled) { value = buildBottomText() }
        addSource(roomState) { value = buildBottomText() }
        addSource(currentStreamInformation) { value = buildBottomText() }
    }
    val bottomTextEnabled = MediatorLiveData<Boolean>().apply {
        addSource(roomStateEnabled) { value = it || streamInfoEnabled.value ?: true }
        addSource(streamInfoEnabled) { value = it || roomStateEnabled.value ?: true }
    }
    val shouldShowFullscreenHelper = MediatorLiveData<Boolean>().apply {
        addSource(shouldShowInput) { value = !it && bottomTextEnabled.value ?: true }
        addSource(bottomTextEnabled) { value = it && shouldShowInput.value?.not() ?: false }
        addSource(bottomText) {
            value =
                it.isNotBlank() && shouldShowInput.value?.not() ?: false && bottomTextEnabled.value ?: true
        }
    }

    val emoteAndUserSuggestions = MediatorLiveData<List<Suggestion>>().apply {
        addSource(emoteSuggestions) { value = (userSuggestions.value ?: emptyList()).plus(it) }
        addSource(userSuggestions) { value = it.plus(emoteSuggestions.value ?: emptyList()) }
    }

    val emoteItems = emotes.switchMap { emotes ->
        liveData(Dispatchers.Default) {
            val groupedByType = emotes.groupBy {
                when (it.emoteType) {
                    is EmoteType.ChannelTwitchEmote -> EmoteMenuTab.SUBS
                    is EmoteType.ChannelFFZEmote, is EmoteType.ChannelBTTVEmote -> EmoteMenuTab.CHANNEL
                    else -> EmoteMenuTab.GLOBAL
                }
            }

            val groupedWithHeaders = mutableListOf(
                groupedByType[EmoteMenuTab.SUBS].toEmoteItems(),
                groupedByType[EmoteMenuTab.CHANNEL].toEmoteItems(),
                groupedByType[EmoteMenuTab.GLOBAL].toEmoteItems()
            )
            emit(groupedWithHeaders)
        }
    }

    fun getChat(channel: String): LiveData<List<ChatItem>> = twitchRepository.getChat(channel)

    fun loadData(
        oauth: String,
        id: Int,
        load3rdParty: Boolean,
        loadTwitchData: Boolean,
        name: String,
        channelList: List<String> = channels.value ?: emptyList()
    ) {
        val token = when {
            oauth.startsWith("oauth:", true) -> oauth.substringAfter(':')
            else -> oauth
        }
        twitchRepository.loadData(channelList, token, id, load3rdParty, loadTwitchData, name)
    }

    fun setActiveChannel(channel: String) {
        activeChannel.value = channel
    }

    fun setStreamInfoEnabled(enabled: Boolean) {
        streamInfoEnabled.value = enabled
    }

    fun setRoomStateEnabled(enabled: Boolean) {
        roomStateEnabled.value = enabled
    }

    fun clear(channel: String) = twitchRepository.clear(channel)
    fun joinChannel(channel: String? = activeChannel.value): List<String>? {
        if (channel == null) return null
        val current = channels.value ?: emptyList()
        val plus = current.plus(channel)

        channels.value = current.plus(channel)
        twitchRepository.joinChannel(channel)
        return plus
    }
    fun partChannel(): List<String>? {
        val channel = activeChannel.value ?: return null
        val current = channels.value ?: emptyList()
        val minus = current.minus(channel)

        channels.value = minus
        twitchRepository.partChannel(channel)
        twitchRepository.removeChannelData(channel)
        return minus
    }
    fun sendMessage(channel: String, message: String) = twitchRepository.sendMessage(channel, message)
    fun reconnect(onlyIfNecessary: Boolean) = twitchRepository.reconnect(onlyIfNecessary)

    fun close(name: String, oAuth: String, loadTwitchData: Boolean = false, userId: Int = 0) {
        val channels = channels.value ?: emptyList()
        twitchRepository.close {
            connectAndJoinChannels(name, oAuth, channels)
            if (loadTwitchData && userId > 0) loadData(
                oauth = oAuth,
                id = userId,
                load3rdParty = true,
                loadTwitchData = true,
                name = name,
                channelList = channels
            )
        }
    }

    fun reloadEmotes(channel: String, oAuth: String, id: Int) {
        val token = when {
            oAuth.startsWith("oauth:", true) -> oAuth.substringAfter(':')
            else -> oAuth
        }
        twitchRepository.reloadEmotes(channel, token, id)
    }

    fun uploadImage(file: File): Job {
        showUploadProgress.value = true
        return twitchRepository.uploadImage(file)
    }

    fun setMentionEntries(stringSet: Set<String>?) = twitchRepository.setMentionEntries(stringSet)
    fun setBlacklistEntries(stringSet: Set<String>?) =
        twitchRepository.setBlacklistEntries(stringSet)

    fun fetchStreamData(oAuth: String, stringBuilder: (viewers: Int) -> String) {
        val channels = channels.value ?: return
        val token = when {
            oAuth.startsWith("oauth:", true) -> oAuth.substringAfter(':')
            else -> oAuth
        }
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch(coroutineExceptionHandler) {
            timer(STREAM_REFRESH_RATE) {
                val data = mutableMapOf<String, String>()
                channels.forEach { channel ->
                    TwitchApi.getStream(token, channel)?.let {
                        data[channel] = stringBuilder(it.viewers)
                    }
                }
                streamData.value = data
            }
        }
    }

    fun clearIgnores() {
        twitchRepository.clearIgnores()
    }

    fun connectAndJoinChannels(name: String, oAuth: String, channelList: List<String>? = channels.value) {
        if (!twitchRepository.startedConnection) {
            if (channelList?.isEmpty() == true) {
                twitchRepository.connect(name, oAuth)
            } else {
                channelList?.forEachIndexed { i, channel ->
                    if (i == 0) twitchRepository.connect(name, oAuth)
                    twitchRepository.joinChannel(channel)
                }
            }
        }
    }

    private fun buildBottomText(): String {
        var roomStateText = ""
        var streamInfoText = ""

        roomState.value?.let {
            if (roomStateEnabled.value == true) roomStateText = it.toString()
        }
        currentStreamInformation.value?.let {
            if (streamInfoEnabled.value == true) streamInfoText = it
        }

        val stateNotBlank = roomStateText.isNotBlank()
        val streamNotBlank = streamInfoText.isNotBlank()

        return when {
            stateNotBlank && streamNotBlank -> "$roomStateText - $streamInfoText"
            stateNotBlank -> roomStateText
            streamNotBlank -> streamInfoText
            else -> ""
        }
    }

    companion object {
        private val TAG = DankChatViewModel::class.java.simpleName
        private const val STREAM_REFRESH_RATE = 30_000L
    }
}