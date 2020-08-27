package com.flxrs.dankchat

import android.util.Log
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import com.flxrs.dankchat.chat.menu.EmoteMenuTab
import com.flxrs.dankchat.chat.suggestion.Suggestion
import com.flxrs.dankchat.service.ChatRepository
import com.flxrs.dankchat.service.DataRepository
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.state.DataLoadingState
import com.flxrs.dankchat.service.state.ImageUploadState
import com.flxrs.dankchat.service.twitch.connection.SystemMessageType
import com.flxrs.dankchat.service.twitch.emote.EmoteType
import com.flxrs.dankchat.utils.SingleLiveEvent
import com.flxrs.dankchat.utils.extensions.*
import kotlinx.coroutines.*
import java.io.File

class DankChatViewModel @ViewModelInject constructor(
    @Assisted savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val dataRepository: DataRepository
) : ViewModel() {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, Log.getStackTraceString(t))
        _errorEvent.postValue(t)
        val dataEvent = dataLoadingEvent.value
        val imageEvent = imageUploadedEvent.value

        if (dataEvent is DataLoadingState.Loading) {
            _dataLoadingEvent.postValue(DataLoadingState.Failed(t, dataEvent.parameters))
        } else if (imageEvent is ImageUploadState.Loading) {
            _imageUploadedEvent.postValue(ImageUploadState.Failed(t.message, imageEvent.mediaFile))
        }
    }
    private var fetchTimerJob: Job? = null

    val activeChannel = MutableLiveData<String>()
    val channels = MutableLiveData<List<String>>(emptyList())
    val mentionCounts = chatRepository.mentionCounts.asLiveData(coroutineExceptionHandler)

    private val _errorEvent = SingleLiveEvent<Throwable>()
    private val _dataLoadingEvent = SingleLiveEvent<DataLoadingState>()
    private val _imageUploadedEvent = SingleLiveEvent<ImageUploadState>()
    private val streamInfoEnabled = MutableLiveData(true)
    private val roomStateEnabled = MutableLiveData(true)
    private val streamData: MutableLiveData<Map<String, String>> = MutableLiveData()

    private val emotes = activeChannel.switchMap { dataRepository.getEmotes(it).asLiveData(coroutineExceptionHandler) }
    private val roomState = activeChannel.switchMap { chatRepository.getRoomState(it).asLiveData(coroutineExceptionHandler) }
    private val users = activeChannel.switchMap { chatRepository.getUsers(it).asLiveData(coroutineExceptionHandler) }
    private val supibotCommands = activeChannel.switchMap { dataRepository.getSupibotCommands(it).asLiveData(coroutineExceptionHandler) }
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
    private val supibotCommandSuggestions = supibotCommands.switchMap { commands ->
        liveData(Dispatchers.Default) {
            emit(commands.map { Suggestion.CommandSuggestion("$$it") })
        }
    }

    var started = false
    var lastMessage: Map<String, String> = chatRepository.lastMessage

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
    val connectionState = activeChannel.switchMap { chatRepository.getConnectionState(it).asLiveData(coroutineExceptionHandler) }
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

    val suggestions = MediatorLiveData<List<Suggestion>>().apply {
        addSource(emoteSuggestions) { value = userSuggestions.asSuggestionOrEmpty + supibotCommandSuggestions.asSuggestionOrEmpty + it }
        addSource(userSuggestions) { value = it + supibotCommandSuggestions.asSuggestionOrEmpty + emoteSuggestions.asSuggestionOrEmpty }
        addSource(supibotCommandSuggestions) { value = userSuggestions.asSuggestionOrEmpty + it + emoteSuggestions.asSuggestionOrEmpty }
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
                groupedByType[EmoteMenuTab.SUBS].moveToFront(activeChannel.value).toEmoteItems(),
                groupedByType[EmoteMenuTab.CHANNEL].toEmoteItems(),
                groupedByType[EmoteMenuTab.GLOBAL].toEmoteItems()
            )
            emit(groupedWithHeaders)
        }
    }

    fun loadData(dataLoadingParameters: DataLoadingState.Parameters) = loadData(
        oAuth = dataLoadingParameters.oAuth,
        id = dataLoadingParameters.id,
        name = dataLoadingParameters.name,
        loadTwitchData = dataLoadingParameters.loadTwitchData,
        loadHistory = dataLoadingParameters.loadHistory,
        loadSupibot = dataLoadingParameters.loadSupibot
    )

    fun loadData(
        oAuth: String,
        id: String,
        name: String,
        channelList: List<String> = channels.value ?: emptyList(),
        loadTwitchData: Boolean,
        loadHistory: Boolean,
        loadSupibot: Boolean,
        scrollBackLength: Int? = null
    ) {
        scrollBackLength?.let { chatRepository.scrollbackLength = it }

        viewModelScope.launch {
            _dataLoadingEvent.postValue(
                DataLoadingState.Loading(DataLoadingState.Parameters(oAuth, id, name, channelList, loadTwitchData = loadTwitchData, loadHistory = loadHistory, loadSupibot = loadSupibot))
            )

            supervisorScope {
                loadInitialData(oAuth.removeOAuthSuffix, id, channelList, loadTwitchData, loadSupibot).joinAll()

                // depends on previously loaded data
                channelList.map {
                    dataRepository.setEmotesForSuggestions(it)
                    launch(coroutineExceptionHandler) { chatRepository.loadRecentMessages(it, loadHistory) }
                }.joinAll()

                _dataLoadingEvent.postValue(DataLoadingState.Finished)
            }
        }
    }

    fun setSupibotSuggestions(enabled: Boolean) = viewModelScope.launch(coroutineExceptionHandler) {
        when {
            enabled -> dataRepository.loadSupibotCommands()
            else -> dataRepository.clearSupibotCommands()
        }
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

    fun setScrollbackLength(scrollBackLength: Int) {
        chatRepository.scrollbackLength = scrollBackLength
    }

    fun clear(channel: String) = chatRepository.clear(channel)

    fun clearMentionCount(channel: String) = chatRepository.clearMentionCount(channel)

    fun joinChannel(channel: String? = activeChannel.value): List<String>? {
        if (channel == null) return null
        val current = channels.value ?: emptyList()
        val plus = current + channel

        channels.value = current + channel
        chatRepository.joinChannel(channel)
        return plus
    }

    fun partChannel(): List<String>? {
        val channel = activeChannel.value ?: return null
        val current = channels.value ?: emptyList()
        val minus = current - channel

        channels.value = minus
        chatRepository.partChannel(channel)
        chatRepository.removeChannelData(channel)
        return minus
    }

    fun sendMessage(channel: String, message: String) =
        chatRepository.sendMessage(channel, message)

    fun reconnect(onlyIfNecessary: Boolean) = chatRepository.reconnect(onlyIfNecessary)

    fun close(name: String, oAuth: String, userId: String, loadTwitchData: Boolean = false) {
        val channels = channels.value ?: emptyList()
        val didClose = chatRepository.close { connectAndJoinChannels(name, oAuth, channels) }
        if (!didClose) {
            connectAndJoinChannels(name, oAuth, channels, forceConnect = true)
        }

        if (loadTwitchData && oAuth.isNotBlank()) loadData(
            oAuth = oAuth,
            id = userId,
            name = name,
            channelList = channels,
            loadTwitchData = true,
            loadHistory = false,
            loadSupibot = false
        )
    }

    fun reloadEmotes(channel: String, oAuth: String, id: String) = viewModelScope.launch {
        _dataLoadingEvent.postValue(
            DataLoadingState.Loading(
                DataLoadingState.Parameters(
                    oAuth = oAuth,
                    id = id,
                    channels = listOf(channel),
                    isReloadEmotes = true
                )
            )
        )

        val fixedOAuth = oAuth.removeOAuthSuffix
        listOf(
            launch(coroutineExceptionHandler) { dataRepository.loadChannelData(channel, fixedOAuth, forceReload = true) },
            launch(coroutineExceptionHandler) { dataRepository.loadTwitchEmotes(fixedOAuth, id) },
            launch(coroutineExceptionHandler) { dataRepository.loadDankChatBadges() },
        ).joinAll()
        _dataLoadingEvent.postValue(DataLoadingState.Reloaded)
    }

    fun uploadMedia(file: File) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _imageUploadedEvent.postValue(ImageUploadState.Loading(file))
            val url = dataRepository.uploadMedia(file)
            val state = url?.let {
                file.delete()
                ImageUploadState.Finished(it)
            } ?: ImageUploadState.Failed(null, file)
            _imageUploadedEvent.postValue(state)
        }
    }

    fun setMentionEntries(stringSet: Set<String>?) = viewModelScope.launch(coroutineExceptionHandler) { chatRepository.setMentionEntries(stringSet) }
    fun setBlacklistEntries(stringSet: Set<String>?) = viewModelScope.launch(coroutineExceptionHandler) { chatRepository.setBlacklistEntries(stringSet) }

    suspend fun fetchStreamData(oAuth: String, stringBuilder: (viewers: Int) -> String) = withContext(coroutineExceptionHandler) {
        fetchTimerJob?.cancel()
        val channels = channels.value ?: return@withContext
        val fixedOAuth = oAuth.removeOAuthSuffix

        fetchTimerJob = timer(STREAM_REFRESH_RATE) {
            val data = mutableMapOf<String, String>()
            channels.forEach { channel ->
                TwitchApi.getStream(fixedOAuth, channel)?.let {
                    data[channel] = stringBuilder(it.viewers)
                }
            }
            streamData.value = data
        }
    }

    fun clearIgnores() = chatRepository.clearIgnores()

    fun connectAndJoinChannels(name: String, oAuth: String, channelList: List<String>? = channels.value, forceConnect: Boolean = false) {
        if (!chatRepository.startedConnection) {
            when {
                channelList.isNullOrEmpty() -> chatRepository.connect(name, oAuth, forceConnect)
                else -> channelList.forEachIndexed { i, channel ->
                    if (i == 0) chatRepository.connect(name, oAuth, forceConnect)
                    chatRepository.joinChannel(channel)
                }
            }
        }
    }

    private fun CoroutineScope.loadInitialData(oAuth: String, id: String, channelList: List<String>, loadTwitchData: Boolean, loadSupibot: Boolean): List<Job> {
        return listOf(
            launch(coroutineExceptionHandler) { dataRepository.loadDankChatBadges() },
            launch(coroutineExceptionHandler) { dataRepository.loadGlobalBadges() },
            launch(coroutineExceptionHandler) { if (loadTwitchData) dataRepository.loadTwitchEmotes(oAuth, id) },
            launch(coroutineExceptionHandler) { if (loadSupibot) dataRepository.loadSupibotCommands() },
            launch(coroutineExceptionHandler) { chatRepository.loadIgnores(oAuth, id) }
        ) + channelList.map {
            launch(coroutineExceptionHandler) { dataRepository.loadChannelData(it, oAuth) }
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