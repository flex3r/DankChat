package com.flxrs.dankchat.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.chat.menu.EmoteItem
import com.flxrs.dankchat.chat.menu.EmoteMenuTab
import com.flxrs.dankchat.chat.suggestion.Suggestion
import com.flxrs.dankchat.data.ChatRepository
import com.flxrs.dankchat.data.CommandRepository
import com.flxrs.dankchat.data.DataRepository
import com.flxrs.dankchat.data.EmoteUsageRepository
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.data.state.DataLoadingState
import com.flxrs.dankchat.data.state.ImageUploadState
import com.flxrs.dankchat.data.twitch.connection.ConnectionState
import com.flxrs.dankchat.data.twitch.emote.EmoteType
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.data.twitch.emote.ThirdPartyEmoteType
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.utils.extensions.*
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
    private val commandRepository: CommandRepository,
    private val emoteUsageRepository: EmoteUsageRepository,
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

    data class StreamData(val channel: String, val data: String)
    sealed class Event {
        data class Error(val throwable: Throwable) : Event()
    }

    val activeChannel: StateFlow<String> = chatRepository.activeChannel

    private val eventChannel = Channel<Event>(Channel.BUFFERED)
    private val _dataLoadingState = MutableStateFlow<DataLoadingState>(DataLoadingState.None)
    private val _imageUploadedState = MutableStateFlow<ImageUploadState>(ImageUploadState.None)
    private val streamInfoEnabled = MutableStateFlow(true)
    private val roomStateEnabled = MutableStateFlow(true)
    private val streamData = MutableStateFlow<List<StreamData>>(emptyList())
    private val currentSuggestionChannel = MutableStateFlow("")
    private val whisperTabSelected = MutableStateFlow(false)
    private val mentionSheetOpen = MutableStateFlow(false)
    private val preferEmoteSuggestions = MutableStateFlow(false)
    private val _currentStreamedChannel = MutableStateFlow("")
    private val _isFullscreen = MutableStateFlow(false)
    private val shouldShowChips = MutableStateFlow(true)
    private val inputEnabled = MutableStateFlow(true)
    private val isScrolling = MutableStateFlow(false)
    private val chipsExpanded = MutableStateFlow(false)

    private val emotes = currentSuggestionChannel.flatMapLatest { dataRepository.getEmotes(it) }
    private val recentEmotes = emoteUsageRepository.getRecentUsages()
    private val roomStateText = currentSuggestionChannel
        .flatMapLatest { chatRepository.getRoomState(it) }
        .map { it.toDisplayText().ifBlank { null } }
    private val users = currentSuggestionChannel.flatMapLatest { chatRepository.getUsers(it) }
    private val supibotCommands = activeChannel.flatMapLatest { commandRepository.getSupibotCommands(it) }
    private val currentStreamInformation = combine(activeChannel, streamData) { activeChannel, streamData ->
        streamData.find { it.channel == activeChannel }?.data
    }

    private val emoteSuggestions = emotes.mapLatest { emotes ->
        // TODO additional distinct needed?
        emotes.distinctBy { it.code }
            .map { Suggestion.EmoteSuggestion(it) }
    }
    private val userSuggestions = users.mapLatest { users ->
        users.snapshot().keys.map { Suggestion.UserSuggestion(it) }
    }
    private val supibotCommandSuggestions = supibotCommands.mapLatest { commands ->
        commands.map { Suggestion.CommandSuggestion("$$it") }
    }
    private val defaultCommandSuggestions = commandRepository.commands.map { commands ->
        commands.map { Suggestion.CommandSuggestion(it) }
    }


    val events = eventChannel.receiveAsFlow()

    val channels: StateFlow<List<String>?> = chatRepository.channels
        .onEach { channels ->
            if (channels != null && _currentStreamedChannel.value !in channels) {
                _currentStreamedChannel.value = ""
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val channelMentionCount: Flow<Map<String, Int>> = chatRepository.channelMentionCount
    val unreadMessagesMap: Flow<Map<String, Boolean>> = chatRepository.unreadMessagesMap.map { map -> map.filterValues { it } }

    // StateFlow -> Channel -> Flow 4HEad xd
    val imageUploadEventFlow: Flow<ImageUploadState> = _imageUploadedState.produceIn(viewModelScope).receiveAsFlow()
    val dataLoadingEventFlow: Flow<DataLoadingState> = _dataLoadingState.produceIn(viewModelScope).receiveAsFlow()

    val shouldColorNotification: StateFlow<Boolean> =
        combine(chatRepository.hasMentions, chatRepository.hasWhispers) { hasMentions, hasWhispers ->
            hasMentions || hasWhispers
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ConnectionState.DISCONNECTED)
    val canType: StateFlow<Boolean> =
        combine(connectionState, mentionSheetOpen, whisperTabSelected) { connectionState, mentionSheetOpen, whisperTabSelected ->
            val connected = connectionState == ConnectionState.CONNECTED
            (!mentionSheetOpen && connected) || (whisperTabSelected && connected)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val currentBottomText: Flow<String> =
        combine(roomStateText, currentStreamInformation, mentionSheetOpen) { roomState, streamInfo, mentionSheetOpen ->
            listOfNotNull(roomState, streamInfo)
                .takeUnless { mentionSheetOpen }
                ?.joinToString(separator = " - ")
                .orEmpty()
        }

    val shouldShowBottomText: StateFlow<Boolean> =
        combine(
            roomStateEnabled,
            streamInfoEnabled,
            mentionSheetOpen,
            currentBottomText
        ) { roomStateEnabled, streamInfoEnabled, mentionSheetOpen, bottomText ->
            (roomStateEnabled || streamInfoEnabled) && !mentionSheetOpen && bottomText.isNotBlank()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    val shouldShowFullscreenHelper: StateFlow<Boolean> =
        combine(
            shouldShowInput,
            shouldShowBottomText,
            currentBottomText,
            shouldShowViewPager
        ) { shouldShowInput, shouldShowBottomText, bottomText, shouldShowViewPager ->
            !shouldShowInput && shouldShowBottomText && bottomText.isNotBlank() && shouldShowViewPager
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val shouldShowEmoteMenuIcon: StateFlow<Boolean> =
        combine(canType, mentionSheetOpen) { canType, mentionSheetOpen ->
            canType && !mentionSheetOpen
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val suggestions: StateFlow<List<Suggestion>> =
        combine(
            emoteSuggestions,
            userSuggestions,
            supibotCommandSuggestions,
            defaultCommandSuggestions,
            preferEmoteSuggestions
        ) { emotes, users, supibotCommands, defaultCommands, preferEmoteSuggestions ->
            when {
                preferEmoteSuggestions -> (emotes + users + defaultCommands + supibotCommands)
                else                   -> (users + emotes + defaultCommands + supibotCommands)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val emoteItems: Flow<List<List<EmoteItem>>> = combine(emotes, recentEmotes) { emotes, recentEmotes ->
        val availableRecents = recentEmotes.mapNotNull { usage ->
            emotes
                .firstOrNull { it.id == usage.emoteId }
                ?.copy(emoteType = EmoteType.RecentUsageEmote)
        }

        val groupedByType = emotes.groupBy {
            when (it.emoteType) {
                is EmoteType.ChannelTwitchEmote,
                is EmoteType.ChannelTwitchBitEmote,
                is EmoteType.ChannelTwitchFollowerEmote -> EmoteMenuTab.SUBS
                is EmoteType.ChannelFFZEmote,
                is EmoteType.ChannelBTTVEmote,
                is EmoteType.ChannelSevenTVEmote        -> EmoteMenuTab.CHANNEL
                else                                    -> EmoteMenuTab.GLOBAL
            }
        }
        listOf(
            availableRecents.toEmoteItems(),
            groupedByType[EmoteMenuTab.SUBS]?.moveToFront(activeChannel.value).toEmoteItems(),
            groupedByType[EmoteMenuTab.CHANNEL].toEmoteItems(),
            groupedByType[EmoteMenuTab.GLOBAL].toEmoteItems()
        )
    }.flowOn(Dispatchers.Default)

    val isFullscreenFlow: StateFlow<Boolean> = _isFullscreen.asStateFlow()
    val areChipsExpanded: StateFlow<Boolean> = chipsExpanded.asStateFlow()

    val shouldShowChipToggle: StateFlow<Boolean> = combine(shouldShowChips, isScrolling) { shouldShowChips, isScrolling ->
        shouldShowChips && !isScrolling
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    val shouldShowExpandedChips: StateFlow<Boolean> = combine(shouldShowChipToggle, chipsExpanded) { shouldShowChips, chipsExpanded ->
        shouldShowChips && chipsExpanded
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)


    val shouldShowStreamToggle: StateFlow<Boolean> =
        combine(
            shouldShowExpandedChips,
            activeChannel,
            _currentStreamedChannel,
            currentStreamInformation
        ) { canShowChips, activeChannel, currentStream, currentStreamData ->
            canShowChips && activeChannel.isNotBlank() && (currentStream.isNotBlank() || currentStreamData != null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val hasModInChannel: StateFlow<Boolean> =
        combine(shouldShowExpandedChips, activeChannel, chatRepository.userStateFlow) { canShowChips, channel, userState ->
            canShowChips && channel.isNotBlank() && channel in userState.moderationChannels
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val currentStreamedChannel: StateFlow<String> = _currentStreamedChannel.asStateFlow()
    val isStreamActive: Boolean
        get() = currentStreamedChannel.value.isNotBlank()
    val isFullscreen: Boolean
        get() = isFullscreenFlow.value


    val currentRoomState: RoomState
        get() = chatRepository.getRoomState(currentSuggestionChannel.value).firstValue

    fun loadData(dataLoadingParameters: DataLoadingState.Parameters) = loadData(
        oAuth = dataLoadingParameters.oAuth,
        id = dataLoadingParameters.id,
        name = dataLoadingParameters.name,
        isUserChange = dataLoadingParameters.isUserChange,
        loadTwitchData = dataLoadingParameters.loadTwitchData,
        loadThirdPartyData = dataLoadingParameters.loadThirdPartyData,
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
        loadThirdPartyData: Set<ThirdPartyEmoteType>,
        loadHistory: Boolean,
        loadSupibot: Boolean,
        scrollBackLength: Int? = null
    ) {
        scrollBackLength?.let { chatRepository.scrollBackLength = it }

        viewModelScope.launch {
            val parameters = DataLoadingState.Parameters(
                oAuth = oAuth,
                id = id,
                name = name,
                channels = channelList,
                isReloadEmotes = isUserChange,
                loadTwitchData = loadTwitchData,
                loadThirdPartyData = loadThirdPartyData,
                loadHistory = loadHistory,
                loadSupibot = loadSupibot
            )
            val loadingState = DataLoadingState.Loading(parameters)
            _dataLoadingState.emit(loadingState)

            val fixedOauth = oAuth.withoutOAuthSuffix
            val state = runCatchingToState(parameters) { handler ->
                loadInitialData(fixedOauth, id, channelList, loadSupibot, loadThirdPartyData, handler)

                val strictUserStateResult = withTimeoutOrNull(IRC_TIMEOUT_DELAY) {
                    val userState = chatRepository.getLatestValidUserState(minChannelsSize = channelList.size)
                    dataRepository.loadUserStateEmotes(userState.globalEmoteSets, userState.followerEmoteSets)
                }

                if (strictUserStateResult == null) {
                    // try again but potentially without follower emote data
                    withTimeoutOrNull(IRC_TIMEOUT_DELAY) {
                        val userState = chatRepository.getLatestValidUserState(minChannelsSize = 0)
                        dataRepository.loadUserStateEmotes(userState.globalEmoteSets, userState.followerEmoteSets)
                    }
                }

                supervisorScope {
                    launch(handler) { dataRepository.loadTwitchEmotes(fixedOauth, id) }
                }

                // global emote suggestions for whisper tab
                dataRepository.setEmotesForSuggestions("w")
                loadChattersAndMessages(channelList, loadHistory, isUserChange, handler)
            }
            _dataLoadingState.emit(state)
        }
    }

    fun getLastMessage() = chatRepository.getLastMessage()

    fun getChannels() = channels.value.orEmpty()

    fun setSupibotSuggestions(enabled: Boolean) = viewModelScope.launch(coroutineExceptionHandler) {
        when {
            enabled -> commandRepository.loadSupibotCommands()
            else    -> commandRepository.clearSupibotCommands()
        }
    }

    fun setPreferEmotesSuggestions(enabled: Boolean) {
        preferEmoteSuggestions.value = enabled
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

    fun setScrollBackLength(scrollBackLength: Int) {
        chatRepository.scrollBackLength = scrollBackLength
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
    fun clearUnreadMessage(channel: String) = chatRepository.clearUnreadMessage(channel)
    fun reconnect() = chatRepository.reconnect()
    fun joinChannel(channel: String): List<String> = chatRepository.joinChannel(channel)
    fun trySendMessage(message: String) = viewModelScope.launch {
        if (mentionSheetOpen.value && whisperTabSelected.value && !(message.startsWith("/w ") || message.startsWith(".w"))) {
            return@launch
        }

        val channel = currentSuggestionChannel.value
        val commandResult = runCatching {
            commandRepository.checkForCommands(message, channel)
        }.getOrElse {
            eventChannel.send(Event.Error(it))
            return@launch
        }

        when (commandResult) {
            is CommandRepository.CommandResult.Accepted -> chatRepository.makeAndPostCustomSystemMessage(commandResult.response, channel)
            is CommandRepository.CommandResult.Message  -> chatRepository.sendMessage(commandResult.message)
            is CommandRepository.CommandResult.NotFound -> chatRepository.sendMessage(message)
        }
    }

    fun updateChannels(channels: List<String>) = chatRepository.updateChannels(channels)

    fun closeAndReconnect(name: String, oAuth: String, userId: String, loadTwitchData: Boolean = false, loadThirdPartyData: Set<ThirdPartyEmoteType>) {
        chatRepository.closeAndReconnect(name, oAuth)

        if (loadTwitchData && oAuth.isNotBlank()) loadData(
            oAuth = oAuth,
            id = userId,
            name = name,
            channelList = channels.value.orEmpty(),
            isUserChange = true,
            loadTwitchData = true,
            loadThirdPartyData = loadThirdPartyData,
            loadHistory = false,
            loadSupibot = false
        )
    }

    fun reloadEmotes(channel: String, oAuth: String, id: String, loadThirdPartyData: Set<ThirdPartyEmoteType>) = viewModelScope.launch {
        val parameters = DataLoadingState.Parameters(
            oAuth = oAuth,
            id = id,
            channels = listOf(channel),
            isReloadEmotes = true,
            loadThirdPartyData = loadThirdPartyData,
        )
        _dataLoadingState.emit(DataLoadingState.Loading(parameters = parameters))

        val state = runCatchingToState(parameters) { handler ->
            chatRepository.joinChannel(channel = "jtv", listenToPubSub = false)
            val fixedOAuth = oAuth.withoutOAuthSuffix
            supervisorScope {
                listOf(
                    launch(handler) {
                        val channelId = getRoomStateIdIfNeeded(oAuth, channel)
                        dataRepository.loadChannelData(channel, fixedOAuth, channelId, loadThirdPartyData, forceReload = true)
                    },
                    launch(handler) { dataRepository.loadDankChatBadges() },
                ).joinAll()
            }

            withTimeoutOrNull(IRC_TIMEOUT_DELAY) {
                val userState = chatRepository.getLatestValidUserState()
                dataRepository.loadUserStateEmotes(userState.globalEmoteSets, userState.followerEmoteSets)
            }
            supervisorScope {
                launch(handler) { dataRepository.loadTwitchEmotes(fixedOAuth, id) }
            }

            chatRepository.partChannel(channel = "jtv", unListenFromPubSub = false)
            dataRepository.setEmotesForSuggestions(channel)
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
                else             -> ImageUploadState.Failed(result.exceptionOrNull()?.stackTraceToString(), file)
            }
            _imageUploadedState.emit(state)
        }
    }

    fun setMentionEntries(stringSet: Set<String>?) = viewModelScope.launch(coroutineExceptionHandler) { chatRepository.setMentionEntries(stringSet.orEmpty()) }
    fun setBlacklistEntries(stringSet: Set<String>?) = viewModelScope.launch(coroutineExceptionHandler) { chatRepository.setBlacklistEntries(stringSet.orEmpty()) }

    suspend fun fetchStreamData(oAuth: String, stringBuilder: (viewers: Int) -> String) = withContext(coroutineExceptionHandler) {
        fetchTimerJob?.cancel()
        val channels = channels.value
        if (channels.isNullOrEmpty()) return@withContext

        val fixedOAuth = oAuth.withoutOAuthSuffix

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

    fun toggleStream() {
        chipsExpanded.update { false }
        _currentStreamedChannel.update {
            when {
                it.isBlank() -> activeChannel.value
                else         -> ""
            }
        }
    }

    fun setShowChips(value: Boolean) {
        shouldShowChips.value = value
    }

    fun setInputEnabled(value: Boolean) {
        inputEnabled.value = value
    }

    fun toggleFullscreen() {
        chipsExpanded.update { false }
        _isFullscreen.update { !it }
    }

    fun isScrolling(value: Boolean) {
        isScrolling.value = value
    }

    fun toggleChipsExpanded() {
        chipsExpanded.update { !it }
    }

    fun changeRoomState(index: Int, enabled: Boolean, time: String = "") {
        val base = when (index) {
            0 -> ".emoteonly"
            1 -> ".subscribers"
            2 -> ".slow"
            3 -> ".r9kbeta"
            else -> ".followers"
        }

        val command = buildString {
            append(base)

            if (!enabled) {
                append("off")
            }

            if (time.isNotBlank()) {
                append(" $time")
            }
        }

        chatRepository.sendMessage(command)
    }

    fun addEmoteUsage(emote: GenericEmote) = viewModelScope.launch {
        emoteUsageRepository.addEmoteUsage(emote.id)
    }

    fun clearEmoteUsages() = viewModelScope.launch {
        emoteUsageRepository.clearUsages()
    }

    private suspend fun loadInitialData(oAuth: String, id: String, channelList: List<String>, loadSupibot: Boolean, loadThirdPartyData: Set<ThirdPartyEmoteType>, handler: CoroutineExceptionHandler) =
        supervisorScope {
            listOf(
                launch(handler) { dataRepository.loadDankChatBadges() },
                launch(handler) { dataRepository.loadGlobalBadges(oAuth) },
                launch(handler) { if (loadSupibot) commandRepository.loadSupibotCommands() },
                launch(handler) { chatRepository.loadUserBlocks(oAuth, id) }
            ) + channelList.map {
                launch(handler) {
                    val channelId = getRoomStateIdIfNeeded(oAuth, it)
                    dataRepository.loadChannelData(it, oAuth, channelId, loadThirdPartyData)
                }
            }.joinAll()
        }

    private suspend fun loadChattersAndMessages(channelList: List<String>, loadHistory: Boolean, isUserChange: Boolean, handler: CoroutineExceptionHandler) = supervisorScope {
        channelList.map {
            dataRepository.setEmotesForSuggestions(it)
            launch(handler) { chatRepository.loadChatters(it) }
            launch(handler) { chatRepository.loadRecentMessages(it, loadHistory, isUserChange) }
        }.joinAll()
    }

    private suspend fun getRoomStateIdIfNeeded(oAuth: String, channel: String): String? = when {
        oAuth.isNotBlank() -> null
        else               -> withTimeoutOrNull(IRC_TIMEOUT_DELAY) {
            chatRepository.getRoomState(channel).first { it.channelId.isNotBlank() }.channelId
        }
    }

    private suspend fun runCatchingToState(parameters: DataLoadingState.Parameters, block: suspend (CoroutineExceptionHandler) -> Unit): DataLoadingState {
        var failure: Throwable? = null
        val handler = CoroutineExceptionHandler { _, throwable -> failure = throwable }
        val result = runCatching { block(handler) }

        return when {
            result.isFailure || failure != null -> {
                val resultOrFailure = result.exceptionOrNull() ?: failure
                Log.e(TAG, resultOrFailure?.stackTraceToString().orEmpty())

                val message = resultOrFailure?.toString().orEmpty()
                DataLoadingState.Failed(
                    errorMessage = message,
                    parameters = parameters
                )
            }
            else                                -> DataLoadingState.Finished
        }
    }

    companion object {
        private val TAG = MainViewModel::class.java.simpleName
        private const val STREAM_REFRESH_RATE = 30_000L
        private const val IRC_TIMEOUT_DELAY = 10_000L
    }
}