package com.flxrs.dankchat.main

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.chat.FullScreenSheetState
import com.flxrs.dankchat.chat.InputSheetState
import com.flxrs.dankchat.chat.emotemenu.EmoteMenuTab
import com.flxrs.dankchat.chat.emotemenu.EmoteMenuTabItem
import com.flxrs.dankchat.chat.suggestion.Suggestion
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.chat.ChatLoadingFailure
import com.flxrs.dankchat.data.repo.chat.ChatLoadingStep
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.chat.UserStateRepository
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import com.flxrs.dankchat.data.repo.chat.toMergedStrings
import com.flxrs.dankchat.data.repo.command.CommandRepository
import com.flxrs.dankchat.data.repo.command.CommandResult
import com.flxrs.dankchat.data.repo.data.DataLoadingFailure
import com.flxrs.dankchat.data.repo.data.DataLoadingStep
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.data.repo.data.DataUpdateEventMessage
import com.flxrs.dankchat.data.repo.data.toMergedStrings
import com.flxrs.dankchat.data.repo.emote.EmoteUsageRepository
import com.flxrs.dankchat.data.repo.emote.Emotes
import com.flxrs.dankchat.data.state.DataLoadingState
import com.flxrs.dankchat.data.state.ImageUploadState
import com.flxrs.dankchat.data.twitch.chat.ConnectionState
import com.flxrs.dankchat.data.twitch.command.TwitchCommand
import com.flxrs.dankchat.data.twitch.emote.EmoteType
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.data.twitch.message.SystemMessageType.ChannelBTTVEmotesFailed
import com.flxrs.dankchat.data.twitch.message.SystemMessageType.ChannelFFZEmotesFailed
import com.flxrs.dankchat.data.twitch.message.SystemMessageType.ChannelSevenTVEmoteAdded
import com.flxrs.dankchat.data.twitch.message.SystemMessageType.ChannelSevenTVEmoteRemoved
import com.flxrs.dankchat.data.twitch.message.SystemMessageType.ChannelSevenTVEmoteRenamed
import com.flxrs.dankchat.data.twitch.message.SystemMessageType.ChannelSevenTVEmoteSetChanged
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.domain.GetChannelsUseCase
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.stream.StreamsSettingsDataStore
import com.flxrs.dankchat.utils.DateTimeUtils
import com.flxrs.dankchat.utils.extensions.flatMapLatestOrDefault
import com.flxrs.dankchat.utils.extensions.moveToFront
import com.flxrs.dankchat.utils.extensions.timer
import com.flxrs.dankchat.utils.extensions.toEmoteItems
import com.flxrs.dankchat.utils.removeExifAttributes
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.koin.android.annotation.KoinViewModel
import java.io.File
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class MainViewModel(
    private val chatRepository: ChatRepository,
    private val dataRepository: DataRepository,
    private val commandRepository: CommandRepository,
    private val emoteUsageRepository: EmoteUsageRepository,
    private val ignoresRepository: IgnoresRepository,
    private val channelRepository: ChannelRepository,
    private val usersRepository: UsersRepository,
    private val userStateRepository: UserStateRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore,
    private val streamsSettingsDataStore: StreamsSettingsDataStore,
    private val getChannelsUseCase: GetChannelsUseCase,
    chatSettingsDataStore: ChatSettingsDataStore,
) : ViewModel() {

    private var fetchTimerJob: Job? = null
    private var lastDataLoadingJob: Job? = null

    var started = false

    val activeChannel: StateFlow<UserName?> = chatRepository.activeChannel

    private val eventChannel = Channel<MainEvent>(Channel.CONFLATED)
    private val dataLoadingStateChannel = Channel<DataLoadingState>(Channel.CONFLATED)
    private val imageUploadStateChannel = Channel<ImageUploadState>(Channel.CONFLATED)
    private val isImageUploading = MutableStateFlow(false)
    private val isDataLoading = MutableStateFlow(false)
    private val streamData = MutableStateFlow<List<StreamData>>(emptyList())
    private val currentSuggestionChannel = MutableStateFlow<UserName?>(null)
    private val inputSheetState = MutableStateFlow<InputSheetState>(InputSheetState.Closed)
    private val fullScreenSheetState = MutableStateFlow<FullScreenSheetState>(FullScreenSheetState.Closed)
    private val _currentStreamedChannel = MutableStateFlow<UserName?>(null)
    private val _isFullscreen = MutableStateFlow(false)
    private val _isLandscape = MutableStateFlow(false)
    private val isInputFocused = MutableStateFlow(false)
    private val isScrolling = MutableStateFlow(false)
    private val chipsExpanded = MutableStateFlow(false)
    private val repeatedSend = MutableStateFlow(RepeatedSendData(enabled = false, message = ""))

    private val emotes = currentSuggestionChannel
        .flatMapLatestOrDefault(Emotes()) { dataRepository.getEmotes(it) }

    private val recentEmotes = emoteUsageRepository.getRecentUsages().distinctUntilChanged { old, new ->
        new.all { newEmote -> old.any { it.emoteId == newEmote.emoteId } }
    }

    private val roomStateText = combine(
        chatSettingsDataStore.showChatModes,
        currentSuggestionChannel
    ) { roomStateEnabled, channel -> roomStateEnabled to channel }
        .flatMapLatest { (enabled, channel) ->
            when {
                enabled && channel != null -> channelRepository.getRoomStateFlow(channel)
                else                       -> flowOf(null)
            }
        }
        .map { it?.toDisplayText()?.ifBlank { null } }

    private val users = currentSuggestionChannel.flatMapLatestOrDefault(emptySet()) { usersRepository.getUsersFlow(it) }
    private val supibotCommands = currentSuggestionChannel.flatMapLatestOrDefault(emptyList()) { commandRepository.getSupibotCommands(it) }
    private val currentStreamInformation = combine(
        streamsSettingsDataStore.showStreamsInfo,
        activeChannel,
        streamData
    ) { streamInfoEnabled, activeChannel, streamData ->
        streamData.find { it.channel == activeChannel }?.formattedData?.takeIf { streamInfoEnabled }
    }

    private val emoteSuggestions = emotes.mapLatest { emotes ->
        emotes.suggestions.map { Suggestion.EmoteSuggestion(it) }
    }

    private val userSuggestions = users.mapLatest { users ->
        users.map { Suggestion.UserSuggestion(it) }
    }

    private val supibotCommandSuggestions = supibotCommands.mapLatest { commands ->
        commands.map { Suggestion.CommandSuggestion(it) }
    }

    private val commandSuggestions = currentSuggestionChannel.flatMapLatestOrDefault(emptyList()) {
        commandRepository.getCommandTriggers(it)
    }.map { triggers -> triggers.map { Suggestion.CommandSuggestion(it) } }

    private val currentBottomText: Flow<String> =
        combine(roomStateText, currentStreamInformation, fullScreenSheetState) { roomState, streamInfo, chatSheetState ->
            listOfNotNull(roomState, streamInfo)
                .takeUnless { chatSheetState.isOpen }
                ?.joinToString(separator = " - ")
                .orEmpty()
        }

    private val shouldShowBottomText: Flow<Boolean> =
        combine(
            chatSettingsDataStore.showChatModes,
            streamsSettingsDataStore.showStreamsInfo,
            fullScreenSheetState,
            currentBottomText
        ) { roomStateEnabled, streamInfoEnabled, chatSheetState, bottomText ->
            (roomStateEnabled || streamInfoEnabled) && !chatSheetState.isOpen && bottomText.isNotBlank()
        }

    private val loadingFailures = combine(dataRepository.dataLoadingFailures, chatRepository.chatLoadingFailures) { data, chat ->
        data to chat
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Pair(emptySet(), emptySet()))

    private val connectionState = activeChannel
        .flatMapLatestOrDefault(ConnectionState.DISCONNECTED) { chatRepository.getConnectionState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), ConnectionState.DISCONNECTED)

    val channels: StateFlow<List<UserName>?> = chatRepository.channels
        .onEach { channels ->
            if (channels != null && _currentStreamedChannel.value !in channels) {
                _currentStreamedChannel.value = null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), null)

    init {
        viewModelScope.launch {
            repeatedSend.collectLatest {
                if (it.enabled && it.message.isNotBlank()) {
                    while (isActive) {
                        val activeChannel = activeChannel.value ?: break
                        val delay = userStateRepository.getSendDelay(activeChannel)
                        trySendMessageOrCommand(it.message, skipSuspendingCommands = true)
                        delay(delay)
                    }
                }
            }
        }

        viewModelScope.launch {
            dataRepository.dataUpdateEvents.collect { updateEvent ->
                when (updateEvent) {
                    is DataUpdateEventMessage.ActiveEmoteSetChanged -> chatRepository.makeAndPostSystemMessage(
                        type = ChannelSevenTVEmoteSetChanged(updateEvent.actorName, updateEvent.emoteSetName),
                        channel = updateEvent.channel
                    )

                    is DataUpdateEventMessage.EmoteSetUpdated       -> {
                        val (channel, event) = updateEvent
                        event.added.forEach { chatRepository.makeAndPostSystemMessage(ChannelSevenTVEmoteAdded(event.actorName, it.name), channel) }
                        event.updated.forEach { chatRepository.makeAndPostSystemMessage(ChannelSevenTVEmoteRenamed(event.actorName, it.oldName, it.name), channel) }
                        event.removed.forEach { chatRepository.makeAndPostSystemMessage(ChannelSevenTVEmoteRemoved(event.actorName, it.name), channel) }
                    }
                }
            }
        }
    }

    val events = eventChannel.receiveAsFlow()

    val channelMentionCount: SharedFlow<Map<UserName, Int>> = chatRepository.channelMentionCount
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), replay = 1)
    val unreadMessagesMap: SharedFlow<Map<UserName, Boolean>> = chatRepository.unreadMessagesMap
        .mapLatest { map -> map.filterValues { it } }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), replay = 1)

    val imageUploadState = imageUploadStateChannel.receiveAsFlow()
    val dataLoadingState = dataLoadingStateChannel.receiveAsFlow()

    val shouldColorNotification: StateFlow<Boolean> =
        combine(chatRepository.hasMentions, chatRepository.hasWhispers) { hasMentions, hasWhispers ->
            hasMentions || hasWhispers
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val shouldShowViewPager: StateFlow<Boolean> = channels.mapLatest { it?.isNotEmpty() != false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), true)

    val shouldShowTabs: StateFlow<Boolean> = combine(shouldShowViewPager, _isFullscreen, _isLandscape, _currentStreamedChannel) { shouldShowViewPager, isFullscreen, isLandscape, currentStream ->
        shouldShowViewPager && !isFullscreen && !(isLandscape && currentStream != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), true)

    val shouldShowInput: StateFlow<Boolean> = combine(
        appearanceSettingsDataStore.showInput,
        shouldShowViewPager
    ) { inputEnabled, shouldShowViewPager ->
        inputEnabled && shouldShowViewPager
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), true)

    val shouldShowUploadProgress = combine(isImageUploading, isDataLoading) { isUploading, isDataLoading ->
        isUploading || isDataLoading
    }.stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val inputState: StateFlow<InputState> = combine(connectionState, fullScreenSheetState, inputSheetState) { connectionState, chatSheetState, inputSheetState ->
        val inputIsReply = inputSheetState is InputSheetState.Replying || (inputSheetState as? InputSheetState.Emotes)?.previousReply != null
        when (connectionState) {
            ConnectionState.CONNECTED               -> when {
                chatSheetState is FullScreenSheetState.Replies || inputIsReply -> InputState.Replying
                else                                                           -> InputState.Default
            }

            ConnectionState.CONNECTED_NOT_LOGGED_IN -> InputState.NotLoggedIn
            ConnectionState.DISCONNECTED            -> InputState.Disconnected
        }
    }.stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), InputState.Disconnected)

    val canType: StateFlow<Boolean> = combine(connectionState, fullScreenSheetState) { connectionState, fullScreenSheetState ->
        val canTypeInConnectionState = connectionState == ConnectionState.CONNECTED || !appearanceSettingsDataStore.settings.first().autoDisableInput
        (fullScreenSheetState != FullScreenSheetState.Mention && canTypeInConnectionState)
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
        combine(canType, fullScreenSheetState) { canType, chatSheetState ->
            canType && !chatSheetState.isMentionSheet
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val suggestions: StateFlow<Triple<List<Suggestion.UserSuggestion>, List<Suggestion.EmoteSuggestion>, List<Suggestion.CommandSuggestion>>> = combine(
        emoteSuggestions,
        userSuggestions,
        supibotCommandSuggestions,
        commandSuggestions,
    ) { emotes, users, supibotCommands, defaultCommands ->
        Triple(users, emotes, defaultCommands + supibotCommands)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), Triple(emptyList(), emptyList(), emptyList()))

    val emoteTabItems: StateFlow<List<EmoteMenuTabItem>> = combine(emotes, recentEmotes) { emotes, recentEmotes ->
        withContext(Dispatchers.Default) {
            val sortedEmotes = emotes.sorted
            val availableRecents = recentEmotes.mapNotNull { usage ->
                sortedEmotes
                    .firstOrNull { it.id == usage.emoteId }
                    ?.copy(emoteType = EmoteType.RecentUsageEmote)
            }

            val groupedByType = sortedEmotes.groupBy {
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), EmoteMenuTab.entries.map { EmoteMenuTabItem(it, emptyList()) })

    val isFullscreenFlow: StateFlow<Boolean> = _isFullscreen.asStateFlow()
    val areChipsExpanded: StateFlow<Boolean> = chipsExpanded.asStateFlow()

    val shouldShowChipToggle: StateFlow<Boolean> = combine(
        appearanceSettingsDataStore.showChips,
        isScrolling,
        isInputFocused
    ) { shouldShowChips, isScrolling, inputFocus ->
        shouldShowChips && !isScrolling && !inputFocus
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
            canShowChips && activeChannel != null && (currentStream != null || streamData.find { it.channel == activeChannel } != null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val hasModInChannel: StateFlow<Boolean> =
        combine(shouldShowExpandedChips, activeChannel, userStateRepository.userState) { canShowChips, channel, userState ->
            canShowChips && channel != null && channel in userState.moderationChannels
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val useCustomBackHandling: StateFlow<Boolean> = combine(isFullscreenFlow, inputSheetState, fullScreenSheetState) { isFullscreen, inputSheetState, chatSheetState ->
        isFullscreen || inputSheetState.isOpen || chatSheetState.isOpen
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val currentStreamedChannel: StateFlow<UserName?> = _currentStreamedChannel.asStateFlow()

    val shouldEnablePictureInPictureAutoMode: StateFlow<Boolean> = combine(
        currentStreamedChannel,
        streamsSettingsDataStore.pipEnabled,
    ) { currentStream, pipEnabled ->
        currentStream != null && pipEnabled
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)

    val isStreamActive: Boolean
        get() = currentStreamedChannel.value != null
    val isFullscreen: Boolean
        get() = isFullscreenFlow.value
    val isEmoteSheetOpen: Boolean
        get() = inputSheetState.value is InputSheetState.Emotes
    val isReplySheetOpen: Boolean
        get() = inputSheetState.value is InputSheetState.Replying
    val currentReply: InputSheetState.Replying?
        get() = inputSheetState.value as? InputSheetState.Replying
    val isWhisperTabOpen: Boolean
        get() = fullScreenSheetState.value is FullScreenSheetState.Whisper
    val isMentionTabOpen: Boolean
        get() = fullScreenSheetState.value is FullScreenSheetState.Mention

    val isFullScreenSheetClosed: Boolean
        get() = fullScreenSheetState.value is FullScreenSheetState.Closed
    val isInputSheetClosed: Boolean
        get() = inputSheetState.value is InputSheetState.Closed

    val currentRoomState: RoomState?
        get() {
            val channel = currentSuggestionChannel.value ?: return null
            return channelRepository.getRoomState(channel)
        }

    fun cancelDataLoad() {
        lastDataLoadingJob?.cancel()
        viewModelScope.launch {
            clearDataLoadingStates(DataLoadingState.None)
        }
    }

    fun loadData(channelList: List<UserName> = channels.value.orEmpty()) {
        val isLoggedIn = dankChatPreferenceStore.isLoggedIn

        lastDataLoadingJob?.cancel()
        lastDataLoadingJob = viewModelScope.launch {
            clearDataLoadingStates(DataLoadingState.Loading)

            dataRepository.createFlowsIfNecessary(channels = channelList + WhisperMessage.WHISPER_CHANNEL)
            ignoresRepository.loadUserBlocks()

            val channels = getChannelsUseCase(channelList)
            awaitAll(
                async { dataRepository.loadDankChatBadges() },
                async { dataRepository.loadGlobalBadges() },
                async { commandRepository.loadSupibotCommands() },
                async { dataRepository.loadGlobalBTTVEmotes() },
                async { dataRepository.loadGlobalFFZEmotes() },
                async { dataRepository.loadGlobalSevenTVEmotes() },
                *channels.flatMap { (channelId, channel, channelDisplayName) ->
                    chatRepository.createFlowsIfNecessary(channel)
                    listOf(
                        async { dataRepository.loadChannelBadges(channel, channelId) },
                        async { dataRepository.loadChannelBTTVEmotes(channel, channelDisplayName, channelId) },
                        async { dataRepository.loadChannelFFZEmotes(channel, channelId) },
                        async { dataRepository.loadChannelSevenTVEmotes(channel, channelId) },
                        async { chatRepository.loadRecentMessagesIfEnabled(channel) },
                    )
                }.toTypedArray()
            )

            chatRepository.reparseAllEmotesAndBadges()

            if (!isLoggedIn) {
                checkFailuresAndEmitState()
                return@launch
            }

            val userState = userStateRepository.tryGetUserStateOrFallback(minChannelsSize = channelList.size)
            userState?.let {
                dataRepository.loadUserStateEmotes(userState.globalEmoteSets, userState.followerEmoteSets)
            }

            checkFailuresAndEmitState()
        }
    }

    fun retryDataLoading(dataLoadingFailures: Set<DataLoadingFailure>, chatLoadingFailures: Set<ChatLoadingFailure>) {
        lastDataLoadingJob?.cancel()
        lastDataLoadingJob = viewModelScope.launch {
            clearDataLoadingStates(DataLoadingState.Loading)
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
                        is DataLoadingStep.ChannelBTTVEmotes    -> dataRepository.loadChannelBTTVEmotes(it.step.channel, it.step.channelDisplayName, it.step.channelId)
                    }
                }
            } + chatLoadingFailures.map {
                async {
                    Log.d(TAG, "Retrying chat loading step: $it")
                    when (it.step) {
                        is ChatLoadingStep.RecentMessages -> chatRepository.loadRecentMessagesIfEnabled(it.step.channel)
                    }
                }
            }.awaitAll()

            chatRepository.reparseAllEmotesAndBadges()

            checkFailuresAndEmitState()
        }
    }

    fun getLastMessage() = chatRepository.getLastMessage()

    fun getChannels(): List<UserName> = channels.value.orEmpty()

    fun getActiveChannel(): UserName? = activeChannel.value

    fun blockUser() = viewModelScope.launch {
        runCatching {
            if (!dankChatPreferenceStore.isLoggedIn) {
                return@launch
            }

            val activeChannel = getActiveChannel() ?: return@launch
            val channelId = channelRepository.getChannel(activeChannel)?.id ?: return@launch
            ignoresRepository.addUserBlock(channelId, activeChannel)
        }
    }

    fun setActiveChannel(channel: UserName?) {
        repeatedSend.update { it.copy(enabled = false) }
        chatRepository.setActiveChannel(channel)
        currentSuggestionChannel.value = channel
    }

    fun setSuggestionChannel(channel: UserName) {
        currentSuggestionChannel.value = channel
    }

    fun setFullScreenSheetState(state: FullScreenSheetState) {
        repeatedSend.update { it.copy(enabled = false) }
        fullScreenSheetState.update { state }
        when (state) {
            FullScreenSheetState.Whisper -> chatRepository.clearMentionCount(WhisperMessage.WHISPER_CHANNEL) // TODO check clearing when already in whisper tab
            FullScreenSheetState.Mention -> chatRepository.clearMentionCounts()
            else                         -> Unit
        }
    }

    fun setEmoteInputSheetState() {
        inputSheetState.update {
            when (it) {
                is InputSheetState.Emotes -> it
                else                      -> InputSheetState.Emotes(previousReply = it as? InputSheetState.Replying)
            }
        }
    }

    fun setReplyingInputSheetState(replyMessageId: String, replyName: UserName) {
        inputSheetState.update {
            InputSheetState.Replying(replyMessageId, replyName)
        }
    }

    fun closeInputSheet(keepPreviousReply: Boolean = true): InputSheetState {
        inputSheetState.update {
            (it as? InputSheetState.Emotes)?.previousReply?.takeIf { keepPreviousReply } ?: InputSheetState.Closed
        }
        return inputSheetState.value
    }

    fun clear(channel: UserName) = chatRepository.clear(channel)
    fun clearMentionCount(channel: UserName) = chatRepository.clearMentionCount(channel)
    fun clearUnreadMessage(channel: UserName) = chatRepository.clearUnreadMessage(channel)
    fun reconnect() = viewModelScope.launch {
        chatRepository.reconnect()
        dataRepository.reconnect()
    }

    fun joinChannel(channel: UserName): List<UserName> = chatRepository.joinChannel(channel)
    fun trySendMessageOrCommand(message: String, skipSuspendingCommands: Boolean = false) = viewModelScope.launch {
        val channel = currentSuggestionChannel.value ?: return@launch
        val chatState = fullScreenSheetState.value
        val inputSheetState = inputSheetState.value
        val replyIdOrNull = chatState.replyIdOrNull ?: inputSheetState.replyIdOrNull
        val commandResult = runCatching {
            when (chatState) {
                FullScreenSheetState.Whisper -> commandRepository.checkForWhisperCommand(message, skipSuspendingCommands)
                else                         -> {
                    val roomState = currentRoomState ?: return@launch
                    val userState = userStateRepository.userState.value
                    val shouldSkip = skipSuspendingCommands || chatState is FullScreenSheetState.Replies
                    commandRepository.checkForCommands(message, channel, roomState, userState, shouldSkip)
                }
            }
        }.getOrElse {
            eventChannel.send(MainEvent.Error(it))
            return@launch
        }

        when (commandResult) {
            is CommandResult.Accepted,
            is CommandResult.Blocked               -> Unit

            is CommandResult.IrcCommand,
            is CommandResult.NotFound              -> chatRepository.sendMessage(message, replyIdOrNull)

            is CommandResult.AcceptedTwitchCommand -> {
                if (commandResult.command == TwitchCommand.Whisper) {
                    chatRepository.fakeWhisperIfNecessary(message)
                }
                if (commandResult.response != null) {
                    chatRepository.makeAndPostCustomSystemMessage(commandResult.response, channel)
                }
            }

            is CommandResult.AcceptedWithResponse  -> chatRepository.makeAndPostCustomSystemMessage(commandResult.response, channel)
            is CommandResult.Message               -> chatRepository.sendMessage(commandResult.message, replyIdOrNull)
        }

        if (commandResult != CommandResult.NotFound && commandResult != CommandResult.IrcCommand) {
            chatRepository.appendLastMessage(channel, message)
        }
    }

    fun setRepeatedSend(enabled: Boolean, message: String) = repeatedSend.update {
        RepeatedSendData(enabled, message)
    }

    fun updateChannels(channels: List<UserName>) = viewModelScope.launch {
        val removed = chatRepository.updateChannels(channels)
        dataRepository.removeChannels(removed)
    }

    fun closeAndReconnect() {
        chatRepository.closeAndReconnect()
        loadData()
    }

    @SuppressLint("BuildListAdds")
    fun reloadEmotes(channelName: UserName) = viewModelScope.launch {
        val isLoggedIn = dankChatPreferenceStore.isLoggedIn
        dataLoadingStateChannel.send(DataLoadingState.Loading)
        isDataLoading.update { true }

        if (isLoggedIn) {
            // reconnect to retrieve an an up-to-date GLOBALUSERSTATE
            userStateRepository.clearUserStateEmotes()
            chatRepository.reconnect(reconnectPubsub = false)
        }

        val channel = channelRepository.getChannel(channelName)

        buildList {
            this += launch { dataRepository.loadDankChatBadges() }
            this += launch { dataRepository.loadGlobalBTTVEmotes() }
            this += launch { dataRepository.loadGlobalFFZEmotes() }
            this += launch { dataRepository.loadGlobalSevenTVEmotes() }
            if (channel != null) {
                this += launch { dataRepository.loadChannelBadges(channelName, channel.id) }
                this += launch { dataRepository.loadChannelBTTVEmotes(channelName, channel.displayName, channel.id) }
                this += launch { dataRepository.loadChannelFFZEmotes(channelName, channel.id) }
                this += launch { dataRepository.loadChannelSevenTVEmotes(channelName, channel.id) }
            }
        }.joinAll()

        chatRepository.reparseAllEmotesAndBadges()

        if (!isLoggedIn) {
            checkFailuresAndEmitState()
            return@launch
        }

        val channels = channels.value ?: listOf(channel)
        val userState = userStateRepository.tryGetUserStateOrFallback(minChannelsSize = channels.size)
        userState?.let {
            dataRepository.loadUserStateEmotes(userState.globalEmoteSets, userState.followerEmoteSets)
        }

        checkFailuresAndEmitState()
    }

    fun uploadMedia(file: File, imageCapture: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            // only remove exif data if an image was selected
            if (imageCapture) {
                runCatching { file.removeExifAttributes() }
            }

            imageUploadStateChannel.send(ImageUploadState.Loading)
            isImageUploading.update { true }
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
                    ImageUploadState.Failed(message, file, imageCapture)
                }
            )
            imageUploadStateChannel.send(state)
            isImageUploading.update { false }
        }
    }

    fun fetchStreamData(channels: List<UserName>? = this.channels.value) {
        cancelStreamData()
        channels?.ifEmpty { null } ?: return

        viewModelScope.launch {
            val settings = streamsSettingsDataStore.settings.first()
            if (!dankChatPreferenceStore.isLoggedIn || !settings.fetchStreams) {
                return@launch
            }

            fetchTimerJob = timer(STREAM_REFRESH_RATE) {
                val data = dataRepository.getStreams(channels)?.map {
                    val uptime = DateTimeUtils.calculateUptime(it.startedAt)
                    val category = it.category
                        ?.takeIf { settings.showStreamCategory }
                        ?.ifBlank { null }
                    val formatted = dankChatPreferenceStore.formatViewersString(it.viewerCount, uptime, category)

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

    fun setCurrentStream(value: UserName?) {
        _currentStreamedChannel.update { value }
    }

    fun toggleStream() {
        chipsExpanded.update { false }
        _currentStreamedChannel.update {
            when (it) {
                null -> activeChannel.value
                else -> null
            }
        }
    }

    fun setInputFocus(value: Boolean) {
        isInputFocused.value = value
    }

    fun toggleFullscreen() {
        chipsExpanded.update { false }
        _isFullscreen.update { !it }
    }

    fun setIsLandscape(value: Boolean) {
        _isLandscape.update { value }
    }

    fun isScrolling(value: Boolean) {
        isScrolling.value = value
    }

    fun toggleChipsExpanded() {
        chipsExpanded.update { !it }
    }

    fun changeRoomState(index: Int, enabled: Boolean, time: String = "") {
        val base = when (index) {
            0    -> "/emoteonly"
            1    -> "/subscribers"
            2    -> "/slow"
            3    -> "/uniquechat"
            else -> "/followers"
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

        trySendMessageOrCommand(command)
    }

    fun addEmoteUsage(id: String) = viewModelScope.launch {
        emoteUsageRepository.addEmoteUsage(id)
    }

    private suspend fun checkFailuresAndEmitState() {
        val (dataFailures, chatFailures) = loadingFailures.value
        dataFailures.forEach {
            val status = (it.failure as? ApiException)?.status?.value?.toString() ?: "0"
            when (it.step) {
                is DataLoadingStep.ChannelSevenTVEmotes -> chatRepository.makeAndPostSystemMessage(SystemMessageType.ChannelSevenTVEmotesFailed(status), it.step.channel)
                is DataLoadingStep.ChannelBTTVEmotes    -> chatRepository.makeAndPostSystemMessage(ChannelBTTVEmotesFailed(status), it.step.channel)
                is DataLoadingStep.ChannelFFZEmotes     -> chatRepository.makeAndPostSystemMessage(ChannelFFZEmotesFailed(status), it.step.channel)
                else                                    -> Unit
            }
        }

        val steps = dataFailures.map(DataLoadingFailure::step).toMergedStrings() + chatFailures.map(ChatLoadingFailure::step).toMergedStrings()
        val failures = dataFailures.map(DataLoadingFailure::failure) + chatFailures.map(ChatLoadingFailure::failure)
        val state = when (val errorCount = dataFailures.size + chatFailures.size) {
            0    -> DataLoadingState.Finished
            1    -> {
                val step = steps.firstOrNull()
                val failure = failures.firstOrNull()
                val message = buildString {
                    if (step != null) {
                        append(step)
                        append(": ")
                    }
                    append(failure?.toErrorMessage().orEmpty())
                }

                DataLoadingState.Failed(message, errorCount, dataFailures, chatFailures)
            }

            else -> {
                val message = failures
                    .groupBy { it.message }.values
                    .maxBy { it.size }
                    .let {
                        buildString {
                            append(steps.joinToString())

                            val error = it.firstOrNull()?.toErrorMessage()
                            if (error != null) {
                                append("\n")
                                append(error)
                            }
                        }
                    }

                DataLoadingState.Failed(message, errorCount, dataFailures, chatFailures)
            }
        }

        clearDataLoadingStates(state)
    }

    private suspend fun clearDataLoadingStates(state: DataLoadingState) {
        chatRepository.clearChatLoadingFailures()
        dataRepository.clearDataLoadingFailures()
        dataLoadingStateChannel.send(state)
        isDataLoading.update { state is DataLoadingState.Loading }
    }

    private fun Throwable.toErrorMessage(): String {
        return when (this) {
            is JsonConvertException -> (cause as? SerializationException ?: this).toString()

            is ApiException         -> buildString {
                append("${url}(${status.value})")
                if (message != null) {
                    append(": $message")
                }
            }

            else                    -> toString()
        }
    }

    private fun clearEmoteUsages() = viewModelScope.launch {
        emoteUsageRepository.clearUsages()
    }

    private fun clearIgnores() = ignoresRepository.clearIgnores()

    fun clearDataForLogout() {
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()

        dankChatPreferenceStore.clearLogin()
        userStateRepository.clear()

        closeAndReconnect()
        clearIgnores()
        clearEmoteUsages()
    }

    companion object {
        private val TAG = MainViewModel::class.java.simpleName
        private val STREAM_REFRESH_RATE = 30.seconds
    }
}
