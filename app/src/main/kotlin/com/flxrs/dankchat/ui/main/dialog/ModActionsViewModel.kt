package com.flxrs.dankchat.ui.main.dialog

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.ShieldModeRepository
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.stream.StreamData
import com.flxrs.dankchat.data.repo.stream.StreamDataRepository
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.preferences.stream.StreamsSettingsDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@Immutable
data class ModActionsUiState(
    val roomState: RoomState?,
    val shieldModeActive: Boolean?,
    val isBroadcaster: Boolean,
    val isLive: Boolean,
)

@KoinViewModel
class ModActionsViewModel(
    private val shieldModeRepository: ShieldModeRepository,
    private val channelRepository: ChannelRepository,
    private val streamDataRepository: StreamDataRepository,
    private val streamsSettingsDataStore: StreamsSettingsDataStore,
    private val authDataStore: AuthDataStore,
) : ViewModel() {
    private val _state = MutableStateFlow<ModActionsUiState?>(null)
    val state: StateFlow<ModActionsUiState?> = _state.asStateFlow()
    val isActive = _state.map { it != null }

    private var collectJob: Job? = null

    fun show(channel: UserName) {
        collectJob?.cancel()
        val initialRoomState = channelRepository.getRoomState(channel)
        // The sheet opens with what is known, the shield mode is refreshed alongside
        _state.value = createState(
            channel = channel,
            roomState = initialRoomState,
            shieldModeActive = shieldModeRepository.getState(channel).value,
            streams = streamDataRepository.streamData.value,
            fetchStreams = streamsSettingsDataStore.currentSettings.value.fetchStreams,
        )

        collectJob = viewModelScope.launch {
            launch { shieldModeRepository.fetch(channel) }

            val roomStates: Flow<RoomState?> = channelRepository.getRoomStateFlow(channel)
            combine(
                roomStates.onStart { emit(initialRoomState) },
                shieldModeRepository.getState(channel),
                streamDataRepository.streamData,
                streamsSettingsDataStore.fetchStreams,
            ) { roomState, shieldModeActive, streams, fetchStreams ->
                createState(channel, roomState, shieldModeActive, streams, fetchStreams)
            }.collect { _state.value = it }
        }
    }

    fun dismiss() {
        collectJob?.cancel()
        _state.value = null
    }

    private fun createState(
        channel: UserName,
        roomState: RoomState?,
        shieldModeActive: Boolean?,
        streams: List<StreamData>,
        fetchStreams: Boolean,
    ): ModActionsUiState = ModActionsUiState(
        roomState = roomState,
        shieldModeActive = shieldModeActive,
        isBroadcaster = roomState != null && roomState.channelId == authDataStore.userIdString,
        // Without stream data the live state is unknown and the stream actions stay available
        isLive = !fetchStreams || streams.any { it.channel == channel },
    )
}
