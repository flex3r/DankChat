package com.flxrs.dankchat.main

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.chat.menu.EmoteMenuTab
import com.flxrs.dankchat.chat.menu.EmoteMenuTabItem
import com.flxrs.dankchat.chat.suggestion.Suggestion
import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.repo.CommandRepository
import com.flxrs.dankchat.data.repo.CommandResult
import com.flxrs.dankchat.data.repo.EmoteUsageRepository
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.data.repo.chat.ChatLoadingFailure
import com.flxrs.dankchat.data.repo.chat.ChatLoadingStep
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.data.DataLoadingFailure
import com.flxrs.dankchat.data.repo.data.DataLoadingStep
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.data.state.DataLoadingState
import com.flxrs.dankchat.data.state.ImageUploadState
import com.flxrs.dankchat.data.twitch.command.TwitchCommand
import com.flxrs.dankchat.data.twitch.connection.ConnectionState
import com.flxrs.dankchat.data.twitch.emote.EmoteType
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.Preference
import com.flxrs.dankchat.utils.DateTimeUtils
import com.flxrs.dankchat.utils.extensions.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds


@HiltViewModel
class MainViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val dataRepository: DataRepository,
    private val commandRepository: CommandRepository,
    private val emoteUsageRepository: EmoteUsageRepository,
    private val ignoresRepository: IgnoresRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
) : ViewModel() {

    private var fetchTimerJob: Job? = null

    var started = false

    data class StreamData(val channel: String, val formattedData: String)
    data class RepeatedSendData(val enabled: Boolean, val message: String)
    sealed class Event {
        data class Error(val throwable: Throwable) : Event()
    }

    val activeChannel: StateFlow<String> = chatRepository.activeChannel

    private val eventChannel = Channel<Event>(Channel.CONFLATED)
    private val _dataLoadingState = MutableStateFlow<DataLoadingState>(DataLoadingState.None)
    private val _imageUploadedState = MutableStateFlow<ImageUploadState>(ImageUploadState.None)
    private val streamInfoEnabled = MutableStateFlow(true)
    private val roomStateEnabled = MutableStateFlow(true)
    private val streamData = MutableStateFlow<List<StreamData>>(emptyList())
    private val currentSuggestionChannel = MutableStateFlow("")
    private val whisperTabSelected = MutableStateFlow(false)
    private val emoteSheetOpen = MutableStateFlow(false)
    private val mentionSheetOpen = MutableStateFlow(false)
    private val _currentStreamedChannel = MutableStateFlow("")
    private val _isFullscreen = MutableStateFlow(false)
    private val shouldShowChips = MutableStateFlow(true)
    private val inputEnabled = MutableStateFlow(true)
    private val isScrolling = MutableStateFlow(false)
    private val chipsExpanded = MutableStateFlow(false)
    private val repeatedSend = MutableStateFlow(RepeatedSendData(enabled = false, message = ""))

    private val emotes = currentSuggestionChannel.flatMapLatest { dataRepository.getEmotes(it) }
    private val recentEmotes = emoteUsageRepository
        .getRecentUsages()
        .distinctUntilChanged { old, new -> new.all { newEmote -> old.any { it.emoteId == newEmote.emoteId } } }

    private val roomStateText =
        combine(roomStateEnabled, currentSuggestionChannel) { roomStateEnabled, channel -> roomStateEnabled to channel }
            .flatMapLatest { if (it.first) chatRepository.getRoomState(it.second) else flowOf(null) }
            .map { it?.toDisplayText()?.ifBlank { null } }

    private val users = currentSuggestionChannel.flatMapLatest { chatRepository.getUsers(it) }
    private val supibotCommands = currentSuggestionChannel.flatMapLatest { commandRepository.getSupibotCommands(it) }
    private val currentStreamInformation = combine(streamInfoEnabled, activeChannel, streamData) { streamInfoEnabled, activeChannel, streamData ->
        streamData.find { it.channel == activeChannel }?.formattedData?.takeIf { streamInfoEnabled }
    }

    private val emoteSuggestions = emotes.mapLatest { emotes ->
        emotes.distinctBy { it.code }
            .map { Suggestion.EmoteSuggestion(it) }
    }

    private val userSuggestions = users.mapLatest { users ->
        users.map { Suggestion.UserSuggestion(it) }
    }

    private val supibotCommandSuggestions = supibotCommands.mapLatest { commands ->
        commands.map { Suggestion.CommandSuggestion(it) }
    }

    private val defaultCommandSuggestions = commandRepository.commandTriggers.map { commands ->
        commands.map { Suggestion.CommandSuggestion(it) }
    }

    private val currentBottomText: Flow<String> =
        combine(roomStateText, currentStreamInformation, mentionSheetOpen) { roomState, streamInfo, mentionSheetOpen ->
            listOfNotNull(roomState, streamInfo)
                .takeUnless { mentionSheetOpen }
                ?.joinToString(separator = " - ")
                .orEmpty()
        }

    private val shouldShowBottomText: Flow<Boolean> =
        combine(
            roomStateEnabled,
            streamInfoEnabled,
            mentionSheetOpen,
            currentBottomText
        ) { roomStateEnabled, streamInfoEnabled, mentionSheetOpen, bottomText ->
            (roomStateEnabled || streamInfoEnabled) && !mentionSheetOpen && bottomText.isNotBlank()
        }

    private val loadingFailures = combine(dataRepository.dataLoadingFailures, chatRepository.chatLoadingFailures) { data, chat ->
        data to chat
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Pair(emptySet(), emptySet()))

    init {
        viewModelScope.launch {
            dankChatPreferenceStore.preferenceFlow.collect {
                when (it) {
                    is Preference.RoomState          -> roomStateEnabled.value = it.enabled
                    is Preference.StreamInfo         -> streamInfoEnabled.value = it.enabled
                    is Preference.Input              -> inputEnabled.value = it.enabled
                    is Preference.SupibotSuggestions -> setSupibotSuggestions(it.enabled)
                    is Preference.ScrollBack         -> chatRepository.scrollBackLength = it.length
                    is Preference.Chips              -> shouldShowChips.value = it.enabled
                    is Preference.TimeStampFormat    -> DateTimeUtils.setPattern(it.pattern)
                    is Preference.FetchStreams       -> fetchStreamData(channels.value.orEmpty())
                }
            }
        }

        viewModelScope.launch {
            repeatedSend.collectLatest {
                if (it.enabled && it.message.isNotBlank()) {
                    while (isActive) {
                        val activeChannel = activeChannel.value
                        val delay = chatRepository.userStateFlow.value.getSendDelay(activeChannel)
                        trySendMessageOrCommand(it.message, skipSuspendingCommands = true)
                        delay(delay)
                    }
                }
            }
        }
    }

    val events = eventChannel.receiveAsFlow()

    val channels: StateFlow<List<String>?> = chatRepository.channels
        .onEach { channels ->
            if (channels != null && _currentStreamedChannel.value !in channels) {
                _currentStreamedChannel.value = ""
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), null)

    val channelMentionCount: SharedFlow<Map<String, Int>> = chatRepository.channelMentionCount
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), replay = 1)
    val unreadMessagesMap: SharedFlow<Map<String, Boolean>> = chatRepository.unreadMessagesMap
        .mapLatest { map -> map.filterValues { it } }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), replay = 1)

    val imageUploadEventFlow = _imageUploadedState.asSharedFlow()
    val dataLoadingEventFlow = _dataLoadingState.asSharedFlow()

    val shouldColorNotification: StateFlow<Boolean> =
        combine(chatRepository.hasMentions, chatRepository.hasWhispers) { hasMentions, hasWhispers ->
            hasMentions || hasWhispers
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val shouldShowViewPager: StateFlow<Boolean> = channels.mapLatest { it?.isNotEmpty() ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), true)

    val shouldShowTabs: StateFlow<Boolean> = shouldShowViewPager.combine(_isFullscreen) { shouldShowViewPager, isFullscreen ->
        shouldShowViewPager && !isFullscreen
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), true)

    val shouldShowInput: StateFlow<Boolean> = combine(inputEnabled, shouldShowViewPager) { inputEnabled, shouldShowViewPager ->
        inputEnabled && shouldShowViewPager
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), true)

    val shouldShowUploadProgress = combine(_imageUploadedState, _dataLoadingState) { imageUploadState, dataLoadingState ->
        imageUploadState is ImageUploadState.Loading || dataLoadingState is DataLoadingState.Loading
    }.stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val connectionState = activeChannel
        .flatMapLatest { chatRepository.getConnectionState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), ConnectionState.DISCONNECTED)

    val canType: StateFlow<Boolean> = combine(connectionState, mentionSheetOpen, whisperTabSelected) { connectionState, mentionSheetOpen, whisperTabSelected ->
        val canTypeInConnectionState = connectionState == ConnectionState.CONNECTED || !dankChatPreferenceStore.autoDisableInput
        (!mentionSheetOpen && canTypeInConnectionState) || (whisperTabSelected && canTypeInConnectionState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    data class BottomTextState(val enabled: Boolean = true, val text: String = "")

    val bottomTextState: StateFlow<BottomTextState> = shouldShowBottomText.combine(currentBottomText) { enabled, text ->
        BottomTextState(enabled, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), BottomTextState())

    val shouldShowFullscreenHelper: StateFlow<Boolean> =
        combine(
            shouldShowInput,
            shouldShowBottomText,
            currentBottomText,
            shouldShowViewPager
        ) { shouldShowInput, shouldShowBottomText, bottomText, shouldShowViewPager ->
            !shouldShowInput && shouldShowBottomText && bottomText.isNotBlank() && shouldShowViewPager
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val shouldShowEmoteMenuIcon: StateFlow<Boolean> =
        combine(canType, mentionSheetOpen) { canType, mentionSheetOpen ->
            canType && !mentionSheetOpen
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val suggestions: StateFlow<Triple<List<Suggestion.UserSuggestion>, List<Suggestion.EmoteSuggestion>, List<Suggestion.CommandSuggestion>>> = combine(
        emoteSuggestions,
        userSuggestions,
        supibotCommandSuggestions,
        defaultCommandSuggestions,
    ) { emotes, users, supibotCommands, defaultCommands ->
        Triple(users, emotes, defaultCommands + supibotCommands)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), Triple(emptyList(), emptyList(), emptyList()))

    val emoteTabItems: Flow<List<EmoteMenuTabItem>> = combine(emotes, recentEmotes) { emotes, recentEmotes ->
        withContext(Dispatchers.Default) {
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
                async { EmoteMenuTabItem(EmoteMenuTab.RECENT, availableRecents.toEmoteItems()) },
                async { EmoteMenuTabItem(EmoteMenuTab.SUBS, groupedByType[EmoteMenuTab.SUBS]?.moveToFront(activeChannel.value).toEmoteItems()) },
                async { EmoteMenuTabItem(EmoteMenuTab.CHANNEL, groupedByType[EmoteMenuTab.CHANNEL].toEmoteItems()) },
                async { EmoteMenuTabItem(EmoteMenuTab.GLOBAL, groupedByType[EmoteMenuTab.GLOBAL].toEmoteItems()) }
            ).awaitAll()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), emptyList())

    val isFullscreenFlow: StateFlow<Boolean> = _isFullscreen.asStateFlow()
    val areChipsExpanded: StateFlow<Boolean> = chipsExpanded.asStateFlow()

    val shouldShowChipToggle: StateFlow<Boolean> = combine(shouldShowChips, isScrolling) { shouldShowChips, isScrolling ->
        shouldShowChips && !isScrolling
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), true)

    val shouldShowExpandedChips: StateFlow<Boolean> = combine(shouldShowChipToggle, chipsExpanded) { shouldShowChips, chipsExpanded ->
        shouldShowChips && chipsExpanded
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)


    val shouldShowStreamToggle: StateFlow<Boolean> =
        combine(
            shouldShowExpandedChips,
            activeChannel,
            _currentStreamedChannel,
            streamData
        ) { canShowChips, activeChannel, currentStream, streamData ->
            canShowChips && activeChannel.isNotBlank() && (currentStream.isNotBlank() || streamData.find { it.channel == activeChannel } != null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val hasModInChannel: StateFlow<Boolean> =
        combine(shouldShowExpandedChips, activeChannel, chatRepository.userStateFlow) { canShowChips, channel, userState ->
            canShowChips && channel.isNotBlank() && channel in userState.moderationChannels
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val useCustomBackHandling: StateFlow<Boolean> = combine(isFullscreenFlow, emoteSheetOpen, mentionSheetOpen) { isFullscreen, emoteSheetOpen, mentionSheetOpen ->
        isFullscreen || emoteSheetOpen || mentionSheetOpen
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val currentStreamedChannel: StateFlow<String> = _currentStreamedChannel.asStateFlow()
    val isStreamActive: Boolean
        get() = currentStreamedChannel.value.isNotBlank()
    val isFullscreen: Boolean
        get() = isFullscreenFlow.value


    val currentRoomState: RoomState
        get() = chatRepository.getRoomState(currentSuggestionChannel.value).firstValue

    fun loadData(channelList: List<String> = channels.value.orEmpty()) {
        val id = dankChatPreferenceStore.userIdString.orEmpty()
        val isLoggedIn = dankChatPreferenceStore.isLoggedIn
        val scrollBackLength = dankChatPreferenceStore.scrollbackLength
        chatRepository.scrollBackLength = scrollBackLength

        viewModelScope.launch {
            val loadingState = DataLoadingState.Loading
            _dataLoadingState.emit(loadingState)

            delay(5.seconds)

            buildList {
                this += async { dataRepository.loadDankChatBadges() }
                this += async { dataRepository.loadGlobalBadges() }
                this += async { commandRepository.loadSupibotCommands() }
                this += async { ignoresRepository.loadUserBlocks(id) }
                this += async { dataRepository.loadGlobalBTTVEmotes() }
                this += async { dataRepository.loadGlobalFFZEmotes() }
                this += async { dataRepository.loadGlobalSevenTVEmotes() }
                channelList.forEach {
                    val channelId = getRoomStateIdOrNull(it) ?: return@forEach
                    this += async { dataRepository.loadChannelBadges(it, channelId) }
                    this += async { dataRepository.loadChannelBTTVEmotes(it, channelId) }
                    this += async { dataRepository.loadChannelFFZEmotes(it, channelId) }
                    this += async { dataRepository.loadChannelSevenTVEmotes(it, channelId) }
                    this += async { chatRepository.loadChatters(it) }
                    this += async { chatRepository.loadRecentMessagesIfEnabled(it) }
                }
            }.awaitAll()

            chatRepository.reparseAllEmotesAndBadges()

            if (!isLoggedIn) {
                return@launch
            }

            val userState = withTimeoutOrNull(IRC_TIMEOUT_DELAY) {
                chatRepository.getLatestValidUserState(minChannelsSize = channelList.size)
            } ?: withTimeoutOrNull(IRC_TIMEOUT_SHORT_DELAY) {
                chatRepository.getLatestValidUserState(minChannelsSize = 0)
            }

            userState?.let {
                dataRepository.loadUserStateEmotes(userState.globalEmoteSets, userState.followerEmoteSets)
            }

            channelList.forEach { dataRepository.setEmotesForSuggestions(it) }

            // global emote suggestions for whisper tab
            dataRepository.setEmotesForSuggestions("w")

            checkFailuresAndEmitState()
        }
    }

    fun retryDataLoading(dataLoadingFailures: Set<DataLoadingFailure>, chatLoadingFailures: Set<ChatLoadingFailure>) = viewModelScope.launch {
        _dataLoadingState.emit(DataLoadingState.Loading)
        dataLoadingFailures.map {
            async {
                Log.d(TAG, "Retrying data loading step: $it")
                when (it.step) {
                    is DataLoadingStep.GlobalSevenTVEmotes  -> dataRepository.loadGlobalSevenTVEmotes()
                    is DataLoadingStep.GlobalBTTVEmotes     -> dataRepository.loadGlobalBTTVEmotes()
                    is DataLoadingStep.GlobalFFZEmotes      -> dataRepository.loadGlobalFFZEmotes()
                    is DataLoadingStep.GlobalBadges         -> dataRepository.loadGlobalBadges()
                    is DataLoadingStep.DankChatBadges       -> dataRepository.loadDankChatBadges()
                    is DataLoadingStep.ChannelBadges        -> dataRepository.loadChannelBadges(it.step.channel, it.step.channelId)
                    is DataLoadingStep.ChannelSevenTVEmotes -> dataRepository.loadChannelSevenTVEmotes(it.step.channel, it.step.channelId)
                    is DataLoadingStep.ChannelFFZEmotes     -> dataRepository.loadChannelFFZEmotes(it.step.channel, it.step.channelId)
                    is DataLoadingStep.ChannelBTTVEmotes    -> dataRepository.loadChannelBTTVEmotes(it.step.channel, it.step.channelId)
                }
            }
        } + chatLoadingFailures.map {
            async {
                Log.d(TAG, "Retrying chat loading step: $it")
                when (it.step) {
                    is ChatLoadingStep.Chatters       -> chatRepository.loadChatters(it.step.channel)
                    is ChatLoadingStep.RecentMessages -> chatRepository.loadRecentMessagesIfEnabled(it.step.channel)
                }
            }
        }.awaitAll()

        chatRepository.reparseAllEmotesAndBadges()

        checkFailuresAndEmitState()
    }

    fun getLastMessage() = chatRepository.getLastMessage()

    fun getChannels() = channels.value.orEmpty()

    fun getActiveChannel() = activeChannel.value.ifEmpty { null }

    fun blockUser() = viewModelScope.launch {
        runCatching {
            if (!dankChatPreferenceStore.isLoggedIn) {
                return@launch
            }

            val activeChannel = getActiveChannel() ?: return@launch
            val channelId = dataRepository.getUserIdByName(activeChannel) ?: return@launch
            ignoresRepository.addUserBlock(channelId, activeChannel)
        }
    }

    fun setActiveChannel(channel: String) {
        repeatedSend.update { it.copy(enabled = false) }
        chatRepository.setActiveChannel(channel)
        currentSuggestionChannel.value = channel
    }

    fun setSuggestionChannel(channel: String) {
        currentSuggestionChannel.value = channel
    }

    fun setMentionSheetOpen(enabled: Boolean) {
        repeatedSend.update { it.copy(enabled = false) }
        mentionSheetOpen.value = enabled
        if (enabled) when (whisperTabSelected.value) {
            true -> chatRepository.clearMentionCount("w")
            else -> chatRepository.clearMentionCounts()
        }
    }

    fun setEmoteSheetOpen(enabled: Boolean) {
        emoteSheetOpen.update { enabled }
    }

    fun setWhisperTabSelected(open: Boolean) {
        repeatedSend.update { it.copy(enabled = false) }
        whisperTabSelected.value = open
        if (mentionSheetOpen.value) {
            when {
                open -> chatRepository.clearMentionCount("w")
                else -> chatRepository.clearMentionCounts()
            }
        }
    }

    fun clear(channel: String) = chatRepository.clear(channel)
    fun clearMentionCount(channel: String) = chatRepository.clearMentionCount(channel)
    fun clearUnreadMessage(channel: String) = chatRepository.clearUnreadMessage(channel)
    fun reconnect() = chatRepository.reconnect()
    fun joinChannel(channel: String): List<String> = chatRepository.joinChannel(channel)
    fun trySendMessageOrCommand(message: String, skipSuspendingCommands: Boolean = false) = viewModelScope.launch {
        if (mentionSheetOpen.value && whisperTabSelected.value && !(message.startsWith("/w ") || message.startsWith(".w"))) {
            return@launch
        }

        val channel = currentSuggestionChannel.value
        val channelId = currentRoomState.channelId
        val commandResult = runCatching {
            commandRepository.checkForCommands(message, channel, channelId, skipSuspendingCommands)
        }.getOrElse {
            eventChannel.send(Event.Error(it))
            return@launch
        }

        when (commandResult) {
            is CommandResult.Accepted              -> Unit
            is CommandResult.AcceptedTwitchCommand -> {
                if (commandResult.command == TwitchCommand.Whisper) {
                    chatRepository.fakeWhisperIfNecessary(message)
                }

                if (commandResult.response != null) {
                    chatRepository.makeAndPostCustomSystemMessage(commandResult.response, channel)
                }
            }

            is CommandResult.AcceptedWithResponse  -> chatRepository.makeAndPostCustomSystemMessage(commandResult.response, channel)
            is CommandResult.Message               -> chatRepository.sendMessage(commandResult.message)
            is CommandResult.NotFound              -> chatRepository.sendMessage(message)
        }

        if (commandResult != CommandResult.NotFound) {
            chatRepository.appendLastMessage(channel, message)
        }
    }

    fun setRepeatedSend(enabled: Boolean, message: String) = repeatedSend.update {
        RepeatedSendData(enabled, message)
    }

    fun updateChannels(channels: List<String>) = chatRepository.updateChannels(channels)

    fun closeAndReconnect() {
        chatRepository.closeAndReconnect()
        loadData()
    }

    fun reloadEmotes(channel: String) = viewModelScope.launch {
        val isLoggedIn = dankChatPreferenceStore.isLoggedIn
        _dataLoadingState.emit(DataLoadingState.Loading)

        if (isLoggedIn) {
            // join a channel to try to get an up-to-date USERSTATE
            chatRepository.joinChannel(channel = "jtv", listenToPubSub = false)
        }

        val channelId = chatRepository.getRoomState(channel)
            .firstValueOrNull?.channelId ?: dataRepository.getUserIdByName(channel)

        buildList {
            this += launch { dataRepository.loadDankChatBadges() }
            this += launch { dataRepository.loadGlobalBTTVEmotes() }
            this += launch { dataRepository.loadGlobalFFZEmotes() }
            this += launch { dataRepository.loadGlobalSevenTVEmotes() }
            if (channelId != null) {
                this += launch { dataRepository.loadChannelBadges(channel, channelId) }
                this += launch { dataRepository.loadChannelBTTVEmotes(channel, channelId) }
                this += launch { dataRepository.loadChannelFFZEmotes(channel, channelId) }
                this += launch { dataRepository.loadChannelSevenTVEmotes(channel, channelId) }
            }
        }.joinAll()

        chatRepository.reparseAllEmotesAndBadges()

        if (!isLoggedIn) {
            return@launch
        }

        withTimeoutOrNull(IRC_TIMEOUT_DELAY) {
            val userState = chatRepository.getLatestValidUserState()
            dataRepository.loadUserStateEmotes(userState.globalEmoteSets, userState.followerEmoteSets)
        }

        chatRepository.partChannel(channel = "jtv", unListenFromPubSub = false)
        dataRepository.setEmotesForSuggestions(channel)

        checkFailuresAndEmitState()
    }

    fun uploadMedia(file: File) {
        viewModelScope.launch {
            _imageUploadedState.emit(ImageUploadState.Loading(file))
            val result = dataRepository.uploadMedia(file)
            val state = result.fold(
                onSuccess = {
                    file.delete()
                    ImageUploadState.Finished(it)
                },
                onFailure = {
                    val message = when (it) {
                        is ApiException -> "${it.status} ${it.message}"
                        else            -> it.stackTraceToString()
                    }
                    ImageUploadState.Failed(message, file)
                }
            )
            _imageUploadedState.emit(state)
        }
    }

    fun fetchStreamData(channels: List<String>) {
        cancelStreamData()

        val fetchingEnabled = dankChatPreferenceStore.fetchStreamInfoEnabled
        if (!dankChatPreferenceStore.isLoggedIn || !fetchingEnabled) {
            return
        }

        viewModelScope.launch {
            fetchTimerJob = timer(STREAM_REFRESH_RATE) {
                val data = dataRepository.getStreams(channels)?.data?.map {
                    val formatted = dankChatPreferenceStore.formatViewersString(it.viewerCount)
                    StreamData(channel = it.userLogin, formattedData = formatted)
                }.orEmpty()

                streamData.value = data
            }
        }
    }

    fun cancelStreamData() {
        fetchTimerJob?.cancel()
        fetchTimerJob = null
        streamData.value = emptyList()
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

    // TODO
    fun changeRoomState(index: Int, enabled: Boolean, time: String = "") {
        val base = when (index) {
            0    -> ".emoteonly"
            1    -> ".subscribers"
            2    -> ".slow"
            3    -> ".r9kbeta"
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

    private suspend fun checkFailuresAndEmitState() {
        val (dataFailures, chatFailures) = loadingFailures.value
        val errorCount = dataFailures.size + chatFailures.size
        val state = when {
            errorCount >= 1 -> {
                val message = dataFailures.firstOrNull()?.failure?.message
                    ?: chatFailures.firstOrNull()?.failure?.message
                    ?: ""
                DataLoadingState.Failed(message, errorCount, dataFailures, chatFailures)
            }

            else            -> DataLoadingState.Finished
        }

        chatRepository.clearChatLoadingFailures()
        dataRepository.clearDataLoadingFailures()
        _dataLoadingState.emit(state)
    }

    private fun clearEmoteUsages() = viewModelScope.launch {
        emoteUsageRepository.clearUsages()
    }

    private fun setSupibotSuggestions(enabled: Boolean) = viewModelScope.launch {
        when {
            enabled -> commandRepository.loadSupibotCommands()
            else    -> commandRepository.clearSupibotCommands()
        }
    }

    private suspend fun getRoomStateIdOrNull(channel: String): String? {
        return chatRepository.getRoomState(channel).firstValueOrNull
            ?.channelId
            ?.ifBlank { null } ?: when {
            dankChatPreferenceStore.isLoggedIn -> dataRepository.getUserIdByName(channel)
            else                               -> withTimeoutOrNull(IRC_TIMEOUT_DELAY) {
                chatRepository.getRoomState(channel).first { it.channelId.isNotBlank() }.channelId
            }
        }
    }

    private fun clearIgnores() = ignoresRepository.clearIgnores()

    fun clearDataForLogout() {
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()

        dankChatPreferenceStore.clearLogin()

        closeAndReconnect()
        clearIgnores()
        clearEmoteUsages()
    }

    companion object {
        private val TAG = MainViewModel::class.java.simpleName
        private const val STREAM_REFRESH_RATE = 30_000L
        private const val IRC_TIMEOUT_DELAY = 5_000L
        private const val IRC_TIMEOUT_SHORT_DELAY = 1_000L
    }
}