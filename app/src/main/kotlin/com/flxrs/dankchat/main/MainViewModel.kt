package com.flxrs.dankchat.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.chat.menu.EmoteItem
import com.flxrs.dankchat.chat.menu.EmoteMenuTab
import com.flxrs.dankchat.chat.suggestion.Suggestion
import com.flxrs.dankchat.service.ChatRepository
import com.flxrs.dankchat.service.DataRepository
import com.flxrs.dankchat.service.api.ApiManager
import com.flxrs.dankchat.service.state.DataLoadingState
import com.flxrs.dankchat.service.state.ImageUploadState
import com.flxrs.dankchat.service.twitch.connection.SystemMessageType
import com.flxrs.dankchat.service.twitch.emote.EmoteType
import com.flxrs.dankchat.utils.extensions.moveToFront
import com.flxrs.dankchat.utils.extensions.removeOAuthSuffix
import com.flxrs.dankchat.utils.extensions.timer
import com.flxrs.dankchat.utils.extensions.toEmoteItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val dataRepository: DataRepository,
    private val apiManager: ApiManager
) : ViewModel() {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, Log.getStackTraceString(t))
        viewModelScope.launch {
            eventChannel.send(Event.Error(t))
        }
    }
    private var fetchTimerJob: Job? = null

    var started = false

    val activeChannel: StateFlow<String> = chatRepository.activeChannel
    val channels: StateFlow<List<String>?> = chatRepository.channels
    val channelMentionCount: Flow<Map<String, Int>> = chatRepository.channelMentionCount
    val shouldColorNotification: StateFlow<Boolean> = combine(chatRepository.hasMentions, chatRepository.hasWhispers) { hasMentions, hasWhispers ->
        hasMentions || hasWhispers
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    data class StreamData(val channel: String, val data: String)
    sealed class Event {
        data class Error(val throwable: Throwable) : Event()
    }

    private val eventChannel = Channel<Event>(Channel.BUFFERED)
    private val _dataLoadingState = MutableStateFlow<DataLoadingState>(DataLoadingState.None)
    private val _imageUploadedState = MutableStateFlow<ImageUploadState>(ImageUploadState.None)
    private val streamInfoEnabled = MutableStateFlow(true)
    private val roomStateEnabled = MutableStateFlow(true)
    private val streamData = MutableStateFlow<List<StreamData>>(emptyList())
    private val currentSuggestionChannel = MutableStateFlow("")
    private val whisperTabSelected = MutableStateFlow(false)
    private val mentionSheetOpen = MutableStateFlow(false)

    private val emotes = currentSuggestionChannel.flatMapLatest { dataRepository.getEmotes(it) }
    private val roomState = currentSuggestionChannel.flatMapLatest { chatRepository.getRoomState(it) }
    private val users = currentSuggestionChannel.flatMapLatest { chatRepository.getUsers(it) }
    private val supibotCommands = activeChannel.flatMapLatest { dataRepository.getSupibotCommands(it) }
    private val currentStreamInformation = combine(activeChannel, streamData) { activeChannel, streamData ->
        streamData.find { it.channel == activeChannel }?.data ?: ""
    }

    private val emoteSuggestions = emotes.mapLatest { emotes ->
        emotes.distinctBy { it.code }
            .map { Suggestion.EmoteSuggestion(it) }
    }
    private val userSuggestions = users.mapLatest { users ->
        users.snapshot().keys.map { Suggestion.UserSuggestion(it) }
    }
    private val supibotCommandSuggestions = supibotCommands.mapLatest { commands ->
        commands.map { Suggestion.CommandSuggestion("$$it") }
    }

    val events = eventChannel.receiveAsFlow()

    // StateFlow -> Channel -> Flow 4HEad xd
    val imageUploadEventFlow: Flow<ImageUploadState> = _imageUploadedState.produceIn(viewModelScope).receiveAsFlow()
    val dataLoadingEventFlow: Flow<DataLoadingState> = _dataLoadingState.produceIn(viewModelScope).receiveAsFlow()

    val inputEnabled = MutableStateFlow(true)
    val appbarEnabled = MutableStateFlow(true)

    val shouldShowViewPager: StateFlow<Boolean> = channels
        .mapLatest { it?.isNotEmpty() ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    val shouldShowInput: StateFlow<Boolean> = combine(inputEnabled, shouldShowViewPager) { inputEnabled, shouldShowViewPager ->
        inputEnabled && shouldShowViewPager
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    val shouldShowUploadProgress = combine(_imageUploadedState, _dataLoadingState) { imageUploadState, dataLoadingState ->
        imageUploadState is ImageUploadState.Loading || dataLoadingState is DataLoadingState.Loading
    }.stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(), false)

    val connectionState = activeChannel
        .flatMapLatest { chatRepository.getConnectionState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SystemMessageType.DISCONNECTED)
    val canType: StateFlow<Boolean> = combine(connectionState, mentionSheetOpen, whisperTabSelected) { connectionState, mentionSheetOpen, whisperTabSelected ->
        val connected = connectionState == SystemMessageType.CONNECTED
        (!mentionSheetOpen && connected) || (whisperTabSelected && connected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val currentBottomText: Flow<String> = combine(roomState, currentStreamInformation, mentionSheetOpen) { roomState, currentStreamInformation, _ ->
        val roomStateText = roomState.toString()
        val streamInfoText = currentStreamInformation

        val stateNotBlank = roomStateText.isNotBlank()
        val streamNotBlank = streamInfoText.isNotBlank()

        when {
            stateNotBlank && streamNotBlank -> "$roomStateText - $streamInfoText"
            stateNotBlank -> roomStateText
            streamNotBlank -> streamInfoText
            else -> ""
        }
    }

    val shouldShowBottomText: StateFlow<Boolean> =
        combine(roomStateEnabled, streamInfoEnabled, mentionSheetOpen, currentBottomText) { roomStateEnabled, streamInfoEnabled, mentionSheetOpen, bottomText ->
            (roomStateEnabled || streamInfoEnabled) && !mentionSheetOpen && bottomText.isNotBlank()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    val shouldShowFullscreenHelper: StateFlow<Boolean> =
        combine(shouldShowInput, shouldShowBottomText, currentBottomText, shouldShowViewPager) { shouldShowInput, shouldShowBottomText, bottomText, shouldShowViewPager ->
            !shouldShowInput && shouldShowBottomText && bottomText.isNotBlank() && shouldShowViewPager
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val shouldShowEmoteMenuIcon: StateFlow<Boolean> =
        combine(canType, mentionSheetOpen) { canType, mentionSheetOpen ->
            canType && !mentionSheetOpen
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val suggestions: Flow<List<Suggestion>> =
        combine(emoteSuggestions, userSuggestions, supibotCommandSuggestions) { emoteSuggestions, userSuggestions, supibotCommandSuggestions ->
            userSuggestions + supibotCommandSuggestions + emoteSuggestions
        }

    val emoteItems: Flow<List<List<EmoteItem>>> = emotes.map { emotes ->
        val groupedByType = emotes.groupBy {
            when (it.emoteType) {
                is EmoteType.ChannelTwitchEmote, is EmoteType.ChannelTwitchBitEmote -> EmoteMenuTab.SUBS
                is EmoteType.ChannelFFZEmote, is EmoteType.ChannelBTTVEmote -> EmoteMenuTab.CHANNEL
                else -> EmoteMenuTab.GLOBAL
            }
        }
        mutableListOf(
            groupedByType[EmoteMenuTab.SUBS].moveToFront(activeChannel.value).toEmoteItems(),
            groupedByType[EmoteMenuTab.CHANNEL].toEmoteItems(),
            groupedByType[EmoteMenuTab.GLOBAL].toEmoteItems()
        )
    }.flowOn(Dispatchers.Default)

    fun loadData(dataLoadingParameters: DataLoadingState.Parameters) = loadData(
        oAuth = dataLoadingParameters.oAuth,
        id = dataLoadingParameters.id,
        name = dataLoadingParameters.name,
        isUserChange = dataLoadingParameters.isUserChange,
        loadTwitchData = dataLoadingParameters.loadTwitchData,
        loadHistory = dataLoadingParameters.loadHistory,
        loadSupibot = dataLoadingParameters.loadSupibot
    )

    fun loadData(
        oAuth: String,
        id: String,
        name: String,
        channelList: List<String> = channels.value.orEmpty(),
        isUserChange: Boolean,
        loadTwitchData: Boolean,
        loadHistory: Boolean,
        loadSupibot: Boolean,
        scrollBackLength: Int? = null
    ) {
        scrollBackLength?.let { chatRepository.scrollbackLength = it }

        viewModelScope.launch {
            val loadingState =
                DataLoadingState.Loading(DataLoadingState.Parameters(oAuth, id, name, channelList, isUserChange, loadTwitchData = loadTwitchData, loadHistory = loadHistory, loadSupibot = loadSupibot))
            _dataLoadingState.emit(loadingState)

            val result = runCatching {
                loadInitialData(oAuth.removeOAuthSuffix, id, channelList, loadTwitchData, loadSupibot)

                // depends on previously loaded data
                chatRepository.userState.take(1).collect { userState ->
                    dataRepository.filterAndSetBitEmotes(userState.emoteSets)
                }
                // global emote suggestions for whisper tab
                dataRepository.setEmotesForSuggestions("w")

                loadChattersAndMessages(channelList, loadHistory, isUserChange)
            }

            val state = when {
                result.isSuccess -> DataLoadingState.Finished
                else -> DataLoadingState.Failed(
                    errorMessage = result.exceptionOrNull()?.message.orEmpty(),
                    parameters = loadingState.parameters
                )
            }
            _dataLoadingState.emit(state)
        }
    }

    fun getLastMessage() = chatRepository.getLastMessage()

    fun getChannels() = channels.value.orEmpty()

    fun setSupibotSuggestions(enabled: Boolean) = viewModelScope.launch(coroutineExceptionHandler) {
        when {
            enabled -> dataRepository.loadSupibotCommands()
            else -> dataRepository.clearSupibotCommands()
        }
    }

    fun setActiveChannel(channel: String) {
        chatRepository.setActiveChannel(channel)
        currentSuggestionChannel.value = channel
    }

    fun setSuggestionChannel(channel: String) {
        currentSuggestionChannel.value = channel
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

    fun setMentionSheetOpen(enabled: Boolean) {
        mentionSheetOpen.value = enabled
        if (enabled) when (whisperTabSelected.value) {
            true -> chatRepository.clearMentionCount("w")
            else -> chatRepository.clearMentionCounts()
        }
    }

    fun setWhisperTabSelected(open: Boolean) {
        whisperTabSelected.value = open
        if (mentionSheetOpen.value) {
            when {
                open -> chatRepository.clearMentionCount("w")
                else -> chatRepository.clearMentionCounts()
            }
        }
    }

    fun clear(channel: String) = chatRepository.clear(channel)
    fun clearIgnores() = chatRepository.clearIgnores()
    fun clearMentionCount(channel: String) = chatRepository.clearMentionCount(channel)
    fun reconnect() = chatRepository.reconnect(false)
    fun joinChannel(channel: String): List<String> = chatRepository.joinChannel(channel)
    fun trySendMessage(message: String) {
        if (mentionSheetOpen.value && whisperTabSelected.value && !message.startsWith("/w ")) {
            return
        }

        chatRepository.sendMessage(message)
    }

    fun updateChannels(channels: List<String>) = chatRepository.updateChannels(channels)

    fun closeAndReconnect(name: String, oAuth: String, userId: String, loadTwitchData: Boolean = false) {
        chatRepository.closeAndReconnect(name, oAuth)

        if (loadTwitchData && oAuth.isNotBlank()) loadData(
            oAuth = oAuth,
            id = userId,
            name = name,
            channelList = channels.value.orEmpty(),
            isUserChange = true,
            loadTwitchData = true,
            loadHistory = false,
            loadSupibot = false
        )
    }

    fun reloadEmotes(channel: String, oAuth: String, id: String) = viewModelScope.launch {
        val parameters = DataLoadingState.Parameters(
            oAuth = oAuth,
            id = id,
            channels = listOf(channel),
            isReloadEmotes = true
        )
        _dataLoadingState.emit(DataLoadingState.Loading(parameters = parameters))

        val result = runCatching {
            chatRepository.joinChannel("jtv")
            val fixedOAuth = oAuth.removeOAuthSuffix
            coroutineScope {
                listOf(
                    launch { dataRepository.loadChannelData(channel, fixedOAuth, forceReload = true) },
                    launch { dataRepository.loadTwitchEmotes(fixedOAuth, id) },
                    launch { dataRepository.loadDankChatBadges() },
                ).joinAll()
            }

            chatRepository.userState.take(1).collect { userState ->
                dataRepository.filterAndSetBitEmotes(userState.emoteSets)
                dataRepository.setEmotesForSuggestions(channel)
            }

            chatRepository.partChannel("jtv")
        }

        val state = when {
            result.isSuccess -> DataLoadingState.Reloaded
            else -> DataLoadingState.Failed(
                errorMessage = result.exceptionOrNull()?.message.orEmpty(),
                parameters = parameters
            )
        }
        _dataLoadingState.emit(state)
    }

    fun uploadMedia(file: File) {
        viewModelScope.launch {
            _imageUploadedState.emit(ImageUploadState.Loading(file))
            val result = runCatching {
                dataRepository.uploadMedia(file)
            }

            val state = when {
                result.isSuccess -> result.getOrNull()?.let {
                    file.delete()
                    ImageUploadState.Finished(it)
                } ?: ImageUploadState.Failed(null, file)
                else -> ImageUploadState.Failed(result.exceptionOrNull()?.message, file)
            }
            _imageUploadedState.emit(state)
        }
    }

    fun setMentionEntries(stringSet: Set<String>?) = viewModelScope.launch(coroutineExceptionHandler) { chatRepository.setMentionEntries(stringSet) }
    fun setBlacklistEntries(stringSet: Set<String>?) = viewModelScope.launch(coroutineExceptionHandler) { chatRepository.setBlacklistEntries(stringSet) }

    suspend fun fetchStreamData(oAuth: String, stringBuilder: (viewers: Int) -> String) = withContext(coroutineExceptionHandler) {
        fetchTimerJob?.cancel()
        val channels = channels.value
        if (channels.isNullOrEmpty()) return@withContext

        val fixedOAuth = oAuth.removeOAuthSuffix

        fetchTimerJob = timer(STREAM_REFRESH_RATE) {
            val streams = runCatching {
                apiManager.getStreams(fixedOAuth, channels)
            }.getOrNull()

            val data = streams?.data?.map {
                StreamData(channel = it.userLogin, data = stringBuilder(it.viewerCount))
            }.orEmpty()

            streamData.value = data
        }
    }

    private suspend fun loadInitialData(oAuth: String, id: String, channelList: List<String>, loadTwitchData: Boolean, loadSupibot: Boolean) = coroutineScope {
        listOf(
            launch { dataRepository.loadDankChatBadges() },
            launch { dataRepository.loadGlobalBadges() },
            launch { if (loadTwitchData) dataRepository.loadTwitchEmotes(oAuth, id) },
            launch { if (loadSupibot) dataRepository.loadSupibotCommands() },
            launch { chatRepository.loadUserBlocks(oAuth, id) }
        ) + channelList.map {
            launch { dataRepository.loadChannelData(it, oAuth) }
        }.joinAll()
    }

    private suspend fun loadChattersAndMessages(channelList: List<String>, loadHistory: Boolean, isUserChange: Boolean) = coroutineScope {
        channelList.map {
            dataRepository.setEmotesForSuggestions(it)
            launch { chatRepository.loadChatters(it) }
            launch { chatRepository.loadRecentMessages(it, loadHistory, isUserChange) }
        }.joinAll()
    }

    companion object {
        private val TAG = MainViewModel::class.java.simpleName
        private const val STREAM_REFRESH_RATE = 30_000L
    }
}