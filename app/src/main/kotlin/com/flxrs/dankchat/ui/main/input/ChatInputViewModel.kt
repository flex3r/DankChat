package com.flxrs.dankchat.ui.main.input

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.chat.ChatConnector
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.chat.SendWaitRepository
import com.flxrs.dankchat.data.repo.chat.UserStateRepository
import com.flxrs.dankchat.data.repo.command.CommandRepository
import com.flxrs.dankchat.data.repo.command.CommandResult
import com.flxrs.dankchat.data.repo.emote.EmoteRepository
import com.flxrs.dankchat.data.repo.emote.EmoteUsageRepository
import com.flxrs.dankchat.data.repo.stream.StreamDataRepository
import com.flxrs.dankchat.data.twitch.chat.ConnectionState
import com.flxrs.dankchat.data.twitch.command.TwitchCommand
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.chat.SuggestionMode
import com.flxrs.dankchat.preferences.chat.SuggestionType
import com.flxrs.dankchat.preferences.notifications.NotificationsSettingsDataStore
import com.flxrs.dankchat.preferences.stream.StreamsSettingsDataStore
import com.flxrs.dankchat.ui.chat.suggestion.Suggestion
import com.flxrs.dankchat.ui.chat.suggestion.SuggestionProvider
import com.flxrs.dankchat.ui.main.InputState
import com.flxrs.dankchat.ui.main.MainEvent
import com.flxrs.dankchat.ui.main.MainEventBus
import com.flxrs.dankchat.ui.main.RepeatedSendData
import com.flxrs.dankchat.ui.main.sheet.FullScreenSheetState
import com.flxrs.dankchat.utils.DateTimeUtils
import com.flxrs.dankchat.utils.TextResource
import com.flxrs.dankchat.utils.extensions.combine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@OptIn(FlowPreview::class)
@KoinViewModel
class ChatInputViewModel(
    private val chatRepository: ChatRepository,
    private val chatChannelProvider: ChatChannelProvider,
    private val chatConnector: ChatConnector,
    private val commandRepository: CommandRepository,
    private val channelRepository: ChannelRepository,
    private val userStateRepository: UserStateRepository,
    private val sendWaitRepository: SendWaitRepository,
    suggestionProvider: SuggestionProvider,
    private val preferenceStore: DankChatPreferenceStore,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore,
    private val notificationsSettingsDataStore: NotificationsSettingsDataStore,
    private val emoteRepository: EmoteRepository,
    private val emoteUsageRepository: EmoteUsageRepository,
    private val mainEventBus: MainEventBus,
    streamsSettingsDataStore: StreamsSettingsDataStore,
    streamDataRepository: StreamDataRepository,
    dispatchersProvider: DispatchersProvider,
) : ViewModel() {
    val textFieldState = TextFieldState()

    private val _isReplying = MutableStateFlow(false)
    private val _replyMessageId = MutableStateFlow<String?>(null)
    private val _replyName = MutableStateFlow<UserName?>(null)
    private val _replyMessage = MutableStateFlow<String?>(null)
    private val repeatedSend = MutableStateFlow(RepeatedSendData(enabled = false, message = ""))
    private val fullScreenSheetState = MutableStateFlow<FullScreenSheetState>(FullScreenSheetState.Closed)
    private val mentionSheetTab = MutableStateFlow(0)
    private val _isEmoteMenuOpen = MutableStateFlow(false)

    private val _whisperTarget = MutableStateFlow<UserName?>(null)
    private var lastWhisperText: String? = null
    val whisperTarget: StateFlow<UserName?> = _whisperTarget.asStateFlow()

    private val _isAnnouncing = MutableStateFlow(false)

    private val codePointCount =
        snapshotFlow {
            val text = textFieldState.text
            text.toString().codePointCount(0, text.length)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val textFlow = snapshotFlow { textFieldState.text.toString() }
    private val textAndCursorFlow =
        snapshotFlow {
            textFieldState.text.toString() to textFieldState.selection.start
        }

    // Debounce text/cursor changes for suggestion lookups
    private val debouncedTextAndCursor = textAndCursorFlow.debounce(SUGGESTION_DEBOUNCE_MS)

    // Get suggestions based on current text, cursor position, and active channel
    val suggestions: StateFlow<ImmutableList<Suggestion>> =
        combine(
            debouncedTextAndCursor,
            chatChannelProvider.activeChannel,
            chatSettingsDataStore.suggestionTypes,
            chatSettingsDataStore.suggestionMode,
        ) { (text, cursorPos), channel, enabledTypes, suggestionMode ->
            SuggestionInput(text, cursorPos, channel, enabledTypes, suggestionMode == SuggestionMode.PrefixOnly)
        }.flatMapLatest { input ->
            suggestionProvider.getSuggestions(input.text, input.cursorPos, input.channel, input.enabledTypes, input.prefixOnly)
        }.map { it.toImmutableList() }
            // Filtering and scoring emotes, emojis and users must not run on the main thread
            .flowOn(dispatchersProvider.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    val characterCounter: StateFlow<CharacterCounterState> =
        combine(
            codePointCount,
            appearanceSettingsDataStore.settings.map { it.showCharacterCounter }.distinctUntilChanged(),
        ) { codePoints, showCharacterCounter ->
            when {
                showCharacterCounter -> CharacterCounterState.Visible(
                    text = "$codePoints/$MESSAGE_CODE_POINT_LIMIT",
                    isOverLimit = codePoints > MESSAGE_CODE_POINT_LIMIT,
                )

                else -> CharacterCounterState.Hidden
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CharacterCounterState.Hidden)

    private val roomStateResources: StateFlow<ImmutableList<TextResource>> =
        combine(
            chatSettingsDataStore.showChatModes,
            chatChannelProvider.activeChannel,
        ) { showModes, channel ->
            showModes to channel
        }.flatMapLatest { (showModes, channel) ->
            if (!showModes || channel == null) {
                flowOf(emptyList())
            } else {
                channelRepository.getRoomStateFlow(channel).map { it.toDisplayTextResources() }
            }
        }.distinctUntilChanged()
            .map { it.toImmutableList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    private val currentStreamInfo: StateFlow<StreamInfoData?> =
        combine(
            streamsSettingsDataStore.showStreamsInfo,
            chatChannelProvider.activeChannel,
            streamDataRepository.streamData,
        ) { streamInfoEnabled, activeChannel, streamData ->
            val data = streamData.find { it.channel == activeChannel }
            when {
                data == null || !streamInfoEnabled -> null
                else -> StreamInfoData(full = data.formattedData, compact = data.formattedDataCompact)
            }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val helperText: StateFlow<HelperText> =
        combine(
            roomStateResources,
            currentStreamInfo,
            appearanceSettingsDataStore.compactChannelInfo,
        ) { roomState, streamInfo, compact ->
            HelperText(
                roomStateParts = roomState.toImmutableList(),
                streamInfo = if (compact) streamInfo?.compact else streamInfo?.full,
                isCompact = compact,
            )
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HelperText())

    private var _uiState: StateFlow<ChatInputUiState>? = null

    init {
        viewModelScope.launch {
            chatChannelProvider.activeChannel.collect {
                repeatedSend.update { data -> data.copy(enabled = false) }
                setReplying(false)
                _isAnnouncing.value = false
            }
        }

        // Clear whisper target when sheet closes or tab switches away from whispers
        viewModelScope.launch {
            combine(fullScreenSheetState, mentionSheetTab) { sheetState, tab ->
                sheetState to tab
            }.collect { (sheetState, tab) ->
                val isWhisperTab = (sheetState is FullScreenSheetState.Mention || sheetState is FullScreenSheetState.Whisper) && tab == 1
                if (!isWhisperTab && _whisperTarget.value != null) {
                    _whisperTarget.value = null
                    textFieldState.clearText()
                }
            }
        }

        viewModelScope.launch {
            repeatedSend.collectLatest {
                if (it.enabled && it.message.isNotBlank()) {
                    while (isActive) {
                        val activeChannel = chatChannelProvider.activeChannel.value ?: break
                        val delay = userStateRepository.getSendDelay(activeChannel)
                        trySendMessageOrCommand(it.message, skipSuspendingCommands = true)
                        delay(delay)
                    }
                }
            }
        }
    }

    fun uiState(
        externalSheetState: StateFlow<FullScreenSheetState>,
        externalMentionTab: StateFlow<Int>,
    ): StateFlow<ChatInputUiState> {
        _uiState?.let { return it }

        // Wire up external sheet state for whisper clearing
        viewModelScope.launch {
            combine(externalSheetState, externalMentionTab) { sheetState, tab ->
                sheetState to tab
            }.collect { (sheetState, tab) ->
                fullScreenSheetState.value = sheetState
                mentionSheetTab.value = tab
            }
        }

        val baseFlow =
            combine(
                textFlow.map { it.isNotBlank() }.distinctUntilChanged(),
                chatChannelProvider.activeChannel,
                chatChannelProvider.activeChannel.flatMapLatest { channel ->
                    if (channel == null) {
                        flowOf(ConnectionState.DISCONNECTED)
                    } else {
                        chatConnector.getConnectionState(channel)
                    }
                },
                appearanceSettingsDataStore.settings.map {
                    InputSettings(
                        autoDisableInput = it.autoDisableInput,
                        showCharacterCounter = it.showCharacterCounter,
                        showSendWaitTimer = it.showSendWaitTimer,
                        showClearInputButton = it.showClearInputButton,
                        showSendButton = it.showSendButton,
                        isCompactMode = it.inputActions.isEmpty(),
                    )
                },
                preferenceStore.isLoggedInFlow,
            ) { hasText, activeChannel, connectionState, inputSettings, isLoggedIn ->
                UiDependencies(hasText, activeChannel, connectionState, isLoggedIn, inputSettings)
            }

        val replyStateFlow =
            combine(
                _isReplying,
                _replyName,
                _replyMessageId,
                _replyMessage,
            ) { isReplying, replyName, replyMessageId, replyMessage ->
                ReplyState(isReplying, replyName, replyMessageId, replyMessage)
            }

        val inputOverlayFlow =
            combine(
                externalSheetState,
                externalMentionTab,
                replyStateFlow,
                _isEmoteMenuOpen,
                _whisperTarget,
                _isAnnouncing,
            ) { sheetState, tab, replyState, isEmoteMenuOpen, whisperTarget, isAnnouncing ->
                InputOverlayState(sheetState, tab, replyState.isReplying, replyState.replyName, replyState.replyMessageId, replyState.replyMessage, isEmoteMenuOpen, whisperTarget, isAnnouncing)
            }

        val sendWaitFlow =
            chatChannelProvider.activeChannel.flatMapLatest { channel ->
                channel?.let(sendWaitRepository::getRemainingSeconds) ?: flowOf(null)
            }

        return combine(
            baseFlow,
            inputOverlayFlow,
            helperText,
            chatSettingsDataStore.userLongClickBehavior,
            chatRepository.lastMessagesFlow,
            sendWaitFlow,
        ) { deps, overlayState, helperText, userLongClickBehavior, _, sendWaitSeconds ->
            val isMentionsTabActive = (overlayState.sheetState is FullScreenSheetState.Mention || overlayState.sheetState is FullScreenSheetState.Whisper) && overlayState.tab == 0
            val isWhisperTabActive = (overlayState.sheetState is FullScreenSheetState.Mention || overlayState.sheetState is FullScreenSheetState.Whisper) && overlayState.tab == 1
            val isInReplyThread = overlayState.sheetState is FullScreenSheetState.Replies
            val effectiveIsReplying = overlayState.isReplying || isInReplyThread
            val canTypeInConnectionState = deps.connectionState == ConnectionState.CONNECTED || !deps.inputSettings.autoDisableInput

            val inputState =
                when (deps.connectionState) {
                    ConnectionState.CONNECTED -> {
                        when {
                            isWhisperTabActive && overlayState.whisperTarget != null -> InputState.Whispering
                            effectiveIsReplying -> InputState.Replying
                            overlayState.isAnnouncing -> InputState.Announcing
                            else -> InputState.Default
                        }
                    }

                    ConnectionState.CONNECTED_NOT_LOGGED_IN -> {
                        InputState.NotLoggedIn
                    }

                    ConnectionState.DISCONNECTED -> {
                        InputState.Disconnected
                    }
                }

            val enabled =
                when {
                    isMentionsTabActive -> false
                    isWhisperTabActive -> deps.isLoggedIn && canTypeInConnectionState && overlayState.whisperTarget != null
                    else -> deps.isLoggedIn && canTypeInConnectionState
                }

            val canSend = deps.hasText && deps.activeChannel != null && deps.connectionState == ConnectionState.CONNECTED && deps.isLoggedIn && enabled

            val effectiveReplyName = overlayState.replyName ?: (overlayState.sheetState as? FullScreenSheetState.Replies)?.replyName
            val effectiveReplyMessage = overlayState.replyMessage.orEmpty()
            val overlay =
                when {
                    overlayState.isReplying && !isInReplyThread && effectiveReplyName != null -> InputOverlay.Reply(effectiveReplyName, effectiveReplyMessage)
                    isWhisperTabActive && overlayState.whisperTarget != null -> InputOverlay.Whisper(overlayState.whisperTarget)
                    overlayState.isAnnouncing -> InputOverlay.Announce
                    else -> InputOverlay.None
                }

            ChatInputUiState(
                canSend = canSend,
                enabled = enabled,
                recentMessages =
                    when {
                        isWhisperTabActive -> lastWhisperText?.let { persistentListOf(it) } ?: persistentListOf()
                        else -> chatRepository.getRecentMessages()
                    },
                activeChannel = deps.activeChannel,
                connectionState = deps.connectionState,
                isLoggedIn = deps.isLoggedIn,
                inputState = inputState,
                overlay = overlay,
                replyMessageId = overlayState.replyMessageId ?: (overlayState.sheetState as? FullScreenSheetState.Replies)?.replyMessageId,
                isEmoteMenuOpen = overlayState.isEmoteMenuOpen,
                helperText = helperText,
                isWhisperTabActive = isWhisperTabActive,
                showClearInputButton = deps.inputSettings.showClearInputButton,
                showSendButton = deps.inputSettings.showSendButton,
                sendWaitTime = sendWaitSeconds?.takeIf { deps.inputSettings.showSendWaitTimer && !isWhisperTabActive }?.let(DateTimeUtils::formatSeconds),
                isCompactMode = deps.inputSettings.isCompactMode,
                userLongClickBehavior = userLongClickBehavior,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatInputUiState()).also { _uiState = it }
    }

    fun sendMessage() {
        val text = textFieldState.text.toString()
        if (text.isNotBlank()) {
            val whisperTarget = _whisperTarget.value
            val isAnnouncing = _isAnnouncing.value
            val messageToSend =
                when {
                    whisperTarget != null -> "/w ${whisperTarget.value} $text"
                    isAnnouncing -> "/announce $text"
                    else -> text
                }
            lastWhisperText = if (whisperTarget != null) text else null
            if (isAnnouncing) {
                _isAnnouncing.value = false
            }
            trackEmoteUsagesInMessage(text)
            trySendMessageOrCommand(messageToSend)
            textFieldState.clearText()
        }
    }

    private fun trackEmoteUsagesInMessage(message: String) {
        val channel = chatChannelProvider.activeChannel.value ?: return
        val emoteIds = emoteRepository.findEmoteIdsInMessage(message, channel)
        for (id in emoteIds) {
            addEmoteUsage(id)
        }
    }

    fun trySendMessageOrCommand(
        message: String,
        skipSuspendingCommands: Boolean = false,
    ) = viewModelScope.launch {
        val channel = chatChannelProvider.activeChannel.value ?: return@launch
        val chatState = fullScreenSheetState.value
        val replyIdOrNull =
            when {
                chatState is FullScreenSheetState.Replies -> chatState.replyMessageId
                _isReplying.value -> _replyMessageId.value
                else -> null
            }

        val commandResult =
            runCatching {
                when (chatState) {
                    FullScreenSheetState.Whisper -> {
                        commandRepository.checkForWhisperCommand(message, skipSuspendingCommands)
                    }

                    else -> {
                        val roomState = channelRepository.getRoomState(channel) ?: return@launch
                        val userState = userStateRepository.userState.value
                        val shouldSkip = skipSuspendingCommands || chatState is FullScreenSheetState.Replies
                        commandRepository.checkForCommands(message, channel, roomState, userState, shouldSkip)
                    }
                }
            }.getOrElse {
                mainEventBus.emitEvent(MainEvent.Error(it))
                return@launch
            }

        when (commandResult) {
            is CommandResult.Accepted,
            is CommandResult.Blocked,
            -> Unit

            is CommandResult.IrcCommand -> {
                chatRepository.sendMessage(message, replyIdOrNull, forceIrc = true)
                setReplying(false)
            }

            is CommandResult.NotFound -> {
                chatRepository.sendMessage(message, replyIdOrNull)
                setReplying(false)
            }

            is CommandResult.UnknownCommand -> {
                val response = TextResource.Res(R.string.cmd_error_unknown_command, persistentListOf(commandResult.trigger))
                chatRepository.makeAndPostCustomSystemMessage(response, channel)
            }

            is CommandResult.AcceptedTwitchCommand -> {
                if (commandResult.command == TwitchCommand.Whisper) {
                    chatRepository.fakeWhisperIfNecessary(message)
                }
                val isWhisperContext =
                    chatState is FullScreenSheetState.Whisper ||
                        (chatState is FullScreenSheetState.Mention && _whisperTarget.value != null)
                if (commandResult.response != null && !isWhisperContext) {
                    chatRepository.makeAndPostCustomSystemMessage(commandResult.response, channel)
                }
            }

            is CommandResult.AcceptedWithResponse -> {
                chatRepository.makeAndPostCustomSystemMessage(commandResult.response, channel)
            }

            is CommandResult.Message -> {
                chatRepository.sendMessage(commandResult.message, replyIdOrNull)
                setReplying(false)
            }
        }

        if (commandResult != CommandResult.NotFound && commandResult != CommandResult.IrcCommand) {
            chatRepository.appendLastMessage(channel, message)
        }
    }

    fun getLastMessage() {
        val message =
            when {
                _whisperTarget.value != null -> lastWhisperText
                else -> chatRepository.getLastMessage()
            } ?: return
        setInputFromHistory(message)
    }

    fun setInputFromHistory(message: String) {
        textFieldState.edit {
            replace(0, length, message)
            placeCursorAtEnd()
        }
    }

    fun setRepeatedSend(enabled: Boolean) {
        val message = textFieldState.text.toString()
        repeatedSend.update {
            RepeatedSendData(enabled, message)
        }
    }

    fun setReplying(
        replying: Boolean,
        replyMessageId: String? = null,
        replyName: UserName? = null,
        replyMessage: String? = null,
    ) {
        _isReplying.value = replying || replyMessageId != null
        _replyMessageId.value = replyMessageId
        _replyName.value = replyName
        _replyMessage.value = replyMessage
    }

    fun setAnnouncing(announcing: Boolean) {
        _isAnnouncing.value = announcing
    }

    fun setWhisperTarget(target: UserName?) {
        _whisperTarget.value = target
        if (target == null) {
            textFieldState.clearText()
        }
    }

    fun mentionUser(
        user: UserName,
        display: DisplayName,
    ) {
        val template = notificationsSettingsDataStore.current().mentionFormat.template
        val mention = "${template.replace("name", user.valueOrDisplayName(display))} "
        insertText(mention)
    }

    fun insertText(text: String) {
        val selection = textFieldState.selection
        textFieldState.edit {
            replace(selection.min, selection.max, text)
            placeCursorBeforeCharAt(selection.min + text.length)
        }
    }

    fun insertEmote(code: String) {
        val selection = textFieldState.selection
        val text = textFieldState.text
        val prefix =
            when {
                selection.min > 0 && text[selection.min - 1] != ' ' -> " "
                else -> ""
            }
        val suffix =
            when {
                selection.max < text.length && text[selection.max] == ' ' -> ""
                else -> " "
            }
        insertText("$prefix$code$suffix")
    }

    fun deleteLastWord() {
        val text = textFieldState.text
        if (text.isEmpty()) return
        var end = text.length
        // Skip trailing spaces
        while (end > 0 && text[end - 1] == ' ') end--
        // Find start of word
        var start = end
        while (start > 0 && text[start - 1] != ' ') start--
        textFieldState.edit {
            replace(start, length, "")
        }
    }

    fun postSystemMessage(message: String) {
        val channel = chatChannelProvider.activeChannel.value ?: return
        chatRepository.makeAndPostCustomSystemMessage(message, channel)
    }

    /**
     * Apply a suggestion to the current input text.
     * Replaces the current word with the suggestion and places cursor at the end.
     */
    fun applySuggestion(suggestion: Suggestion) {
        val currentText = textFieldState.text.toString()
        val cursorPos = textFieldState.selection.start
        val result = computeSuggestionReplacement(currentText, cursorPos, suggestion.toString())

        textFieldState.edit {
            replace(result.replaceStart, result.replaceEnd, result.replacement)
            selection = TextRange(result.newCursorPos)
        }

        if (suggestion is Suggestion.EmoteSuggestion) {
            addEmoteUsage(suggestion.emote.id)
        }
    }

    fun addEmoteUsage(emoteId: String) {
        viewModelScope.launch {
            emoteUsageRepository.addEmoteUsage(emoteId)
        }
    }

    fun setEmoteMenuOpen(open: Boolean) {
        _isEmoteMenuOpen.value = open
    }

    companion object {
        private const val SUGGESTION_DEBOUNCE_MS = 50L
        private const val MESSAGE_CODE_POINT_LIMIT = 500
    }
}

internal data class SuggestionReplacementResult(
    val replaceStart: Int,
    val replaceEnd: Int,
    val replacement: String,
    val newCursorPos: Int,
)

internal fun computeSuggestionReplacement(
    text: String,
    cursorPos: Int,
    suggestionText: String,
): SuggestionReplacementResult {
    val separator = ' '

    // Only look backwards from cursor — match what extractCurrentWord does
    var start = cursorPos
    while (start > 0 && text[start - 1] != separator) start--

    val replacement = suggestionText + separator
    return SuggestionReplacementResult(
        replaceStart = start,
        replaceEnd = cursorPos,
        replacement = replacement,
        newCursorPos = start + replacement.length,
    )
}

private data class SuggestionInput(
    val text: String,
    val cursorPos: Int,
    val channel: UserName?,
    val enabledTypes: List<SuggestionType>,
    val prefixOnly: Boolean,
)

private data class InputSettings(
    val autoDisableInput: Boolean,
    val showCharacterCounter: Boolean,
    val showSendWaitTimer: Boolean,
    val showClearInputButton: Boolean,
    val showSendButton: Boolean,
    val isCompactMode: Boolean,
)

private data class UiDependencies(
    val hasText: Boolean,
    val activeChannel: UserName?,
    val connectionState: ConnectionState,
    val isLoggedIn: Boolean,
    val inputSettings: InputSettings,
)

private data class ReplyState(
    val isReplying: Boolean,
    val replyName: UserName?,
    val replyMessageId: String?,
    val replyMessage: String?,
)

private data class InputOverlayState(
    val sheetState: FullScreenSheetState,
    val tab: Int,
    val isReplying: Boolean,
    val replyName: UserName?,
    val replyMessageId: String?,
    val replyMessage: String?,
    val isEmoteMenuOpen: Boolean,
    val whisperTarget: UserName?,
    val isAnnouncing: Boolean,
)

private data class StreamInfoData(
    val full: String,
    val compact: String,
)
