package com.flxrs.dankchat.ui.main.sheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.chat.ChatNotificationRepository
import com.flxrs.dankchat.ui.chat.history.HistoryChannel
import com.flxrs.dankchat.utils.AppLifecycleListener
import com.flxrs.dankchat.utils.AppLifecycleListener.AppLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class SheetNavigationViewModel(
    chatChannelProvider: ChatChannelProvider,
    chatNotificationRepository: ChatNotificationRepository,
    appLifecycleListener: AppLifecycleListener,
) : ViewModel() {
    private val _fullScreenSheetState = MutableStateFlow<FullScreenSheetState>(FullScreenSheetState.Closed)
    val fullScreenSheetState: StateFlow<FullScreenSheetState> = _fullScreenSheetState.asStateFlow()

    init {
        // The active channel is read while the app is in the foreground and no full screen sheet covers the chat
        viewModelScope.launch {
            combine(appLifecycleListener.appState, chatChannelProvider.activeChannel, _fullScreenSheetState) { appState, channel, sheet ->
                channel.takeIf { appState == AppLifecycle.Foreground && sheet == FullScreenSheetState.Closed }
            }.filterNotNull()
                .collect(chatNotificationRepository::markChannelRead)
        }
    }

    private val _inputSheetState = MutableStateFlow<InputSheetState>(InputSheetState.Closed)

    val sheetState: StateFlow<SheetNavigationState> =
        combine(
            _fullScreenSheetState,
            _inputSheetState,
        ) { fullScreen, input ->
            SheetNavigationState(fullScreenSheet = fullScreen, inputSheet = input)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SheetNavigationState())

    fun openReplies(
        rootMessageId: String,
        replyName: UserName,
    ) {
        _fullScreenSheetState.value = FullScreenSheetState.Replies(rootMessageId, replyName)
    }

    fun openMentions() {
        _fullScreenSheetState.value = FullScreenSheetState.Mention
    }

    fun openWhispers() {
        _fullScreenSheetState.value = FullScreenSheetState.Whisper
    }

    fun openHistory(
        channel: HistoryChannel,
        initialFilter: String = "",
    ) {
        _fullScreenSheetState.value = FullScreenSheetState.History(channel, initialFilter)
    }

    fun closeFullScreenSheet() {
        _fullScreenSheetState.value = FullScreenSheetState.Closed
    }

    fun openDebugInfo() {
        _inputSheetState.value = InputSheetState.DebugInfo
    }

    fun closeInputSheet() {
        _inputSheetState.value = InputSheetState.Closed
    }
}
