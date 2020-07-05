package com.flxrs.dankchat

import android.util.Log
import androidx.lifecycle.*
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.chat.menu.EmoteMenuTab
import com.flxrs.dankchat.chat.suggestion.Suggestion
import com.flxrs.dankchat.service.TwitchRepository
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.state.DataLoadingState
import com.flxrs.dankchat.service.state.ImageUploadState
import com.flxrs.dankchat.service.twitch.connection.SystemMessageType
import com.flxrs.dankchat.service.twitch.emote.EmoteType
import com.flxrs.dankchat.utils.extensions.timer
import com.flxrs.dankchat.utils.extensions.toEmoteItems
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File

class DankChatViewModel(private val twitchRepository: TwitchRepository) : ViewModel() {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, Log.getStackTraceString(t))
        _errorEvent.postValue(t)
    }

    val activeChannel = MutableLiveData<String>()
    val channels = MutableLiveData<List<String>>(emptyList())

    private val _errorEvent = twitchRepository.errorEvent
    private val _dataLoadingEvent = twitchRepository.dataLoadingEvent
    private val _imageUploadedEvent = twitchRepository.imageUploadedEvent
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
    var lastMessage: Map<String, String> = twitchRepository.lastMessage

    val errorEvent: LiveData<Throwable>
        get() = _errorEvent
    val dataLoadingEvent: LiveData<DataLoadingState>
        get() = _dataLoadingEvent
    val imageUploadedEvent: LiveData<ImageUploadState>
        get() = _imageUploadedEvent

    val inputEnabled = MutableLiveData(true)
    val appbarEnabled = MutableLiveData(true)
    val shouldShowViewPager = MediatorLiveData<Boolean>().apply {
        addSource(channels) { value = it.isNotEmpty() }
    }
    val shouldShowInput = MediatorLiveData<Boolean>().apply {
        addSource(inputEnabled) { value = it && shouldShowViewPager.value ?: false }
        addSource(shouldShowViewPager) { value = it && inputEnabled.value ?: true }
    }
    val showUploadProgress = MediatorLiveData<Boolean>().apply {
        addSource(_imageUploadedEvent) { value = it is ImageUploadState.Loading || _dataLoadingEvent.value is DataLoadingState.Loading }
        addSource(_dataLoadingEvent) { value = it is DataLoadingState.Loading || _imageUploadedEvent.value is ImageUploadState.Loading }
    }
    val connectionState = activeChannel.switchMap { twitchRepository.getConnectionState(it) }
    val canType = connectionState.map { it == SystemMessageType.CONNECTED }
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
        addSource(shouldShowInput) { value = shouldShowFullscreenHint(showInput = it) }
        addSource(bottomTextEnabled) { value = shouldShowFullscreenHint(showBottomText = it) }
        addSource(bottomText) { value = shouldShowFullscreenHint(bottomTextEmpty = it.isEmpty()) }
        addSource(shouldShowViewPager) { value = shouldShowFullscreenHint(showViewPager = it) }
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

    fun loadData(oauth: String, id: Int, loadTwitchData: Boolean, loadHistory: Boolean, name: String, channelList: List<String> = channels.value ?: emptyList()) {
        val token = when {
            oauth.startsWith("oauth:", true) -> oauth.substringAfter(':')
            else -> oauth
        }
        twitchRepository.loadData(channelList, token, id, loadTwitchData, loadHistory, name)
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

    fun sendMessage(channel: String, message: String) =
        twitchRepository.sendMessage(channel, message)

    fun reconnect(onlyIfNecessary: Boolean) = twitchRepository.reconnect(onlyIfNecessary)

    fun close(name: String, oAuth: String, loadTwitchData: Boolean = false, userId: Int = 0) {
        val channels = channels.value ?: emptyList()
        val didClose = twitchRepository.close { connectAndJoinChannels(name, oAuth, channels) }
        if (!didClose) {
            connectAndJoinChannels(name, oAuth, channels, forceConnect = true)
        }

        if (loadTwitchData && userId > 0) loadData(
            oauth = oAuth,
            id = userId,
            loadTwitchData = true,
            loadHistory = false,
            name = name,
            channelList = channels
        )
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

    suspend fun fetchStreamData(oAuth: String, stringBuilder: (viewers: Int) -> String) = withContext(coroutineExceptionHandler) {
        val channels = channels.value ?: return@withContext
        val token = when {
            oAuth.startsWith("oauth:", true) -> oAuth.substringAfter(':')
            else -> oAuth
        }
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

    fun clearIgnores() {
        twitchRepository.clearIgnores()
    }

    fun connectAndJoinChannels(name: String, oAuth: String, channelList: List<String>? = channels.value, forceConnect: Boolean = false) {
        if (!twitchRepository.startedConnection) {
            if (channelList?.isEmpty() == true) {
                twitchRepository.connect(name, oAuth, forceConnect)
            } else {
                channelList?.forEachIndexed { i, channel ->
                    if (i == 0) twitchRepository.connect(name, oAuth, forceConnect)
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

    private fun shouldShowFullscreenHint(
        showInput: Boolean = shouldShowInput.value ?: true,
        showBottomText: Boolean = bottomTextEnabled.value ?: true,
        bottomTextEmpty: Boolean = bottomText.value?.isEmpty() ?: false,
        showViewPager: Boolean = shouldShowViewPager.value ?: true
    ): Boolean = !showInput && showBottomText && !bottomTextEmpty && showViewPager

    companion object {
        private val TAG = DankChatViewModel::class.java.simpleName
        private const val STREAM_REFRESH_RATE = 30_000L
    }
}