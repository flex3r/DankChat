package com.flxrs.dankchat

import androidx.lifecycle.*
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.service.TwitchRepository
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.utils.extensions.timer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class DankChatViewModel(private val twitchRepository: TwitchRepository) : ViewModel() {

    private val activeChannel = MutableLiveData<String>()
    private val streamInfoEnabled = MutableLiveData(true)
    private val roomStateEnabled = MutableLiveData(true)
    private val streamData: MutableLiveData<Map<String, String>> = MutableLiveData()
    private val roomState = Transformations.switchMap(activeChannel) {
        twitchRepository.getRoomState(it)
    }
    private val currentStreamInformation = MediatorLiveData<String>().apply {
        addSource(activeChannel) { value = streamData.value?.get(it) ?: "" }
        addSource(streamData) {
            activeChannel.value?.let { channel ->
                value = it[channel] ?: ""
            }
        }
    }

    private var fetchJob: Job? = null

    val appbarEnabled = MutableLiveData<Boolean>(true)
    val shouldShowViewPager = MutableLiveData<Boolean>(false)
    val imageUploadedEvent = twitchRepository.imageUploadedEvent

    val emoteCodes = Transformations.switchMap(activeChannel) { twitchRepository.getEmoteCodes(it) }
    val canType = Transformations.switchMap(activeChannel) { twitchRepository.getCanType(it) }
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

    fun getChat(channel: String): LiveData<List<ChatItem>> = twitchRepository.getChat(channel)

    fun loadData(channel: String, oauth: String, id: Int, load3rdParty: Boolean, reAuth: Boolean) {
        if (channel.isNotBlank()) {
            val token = when {
                oauth.startsWith("oauth:", true) -> oauth.substringAfter(':')
                else -> oauth
            }
            twitchRepository.loadData(channel, token, id, load3rdParty, reAuth)
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

    fun removeChannelData(channel: String) = twitchRepository.removeChannelData(channel)

    fun clear(channel: String) = twitchRepository.clear(channel)

    fun reloadEmotes(channel: String, oauth: String, id: Int) =
        twitchRepository.reloadEmotes(channel, oauth, id)

    fun uploadImage(file: File) = twitchRepository.uploadImage(file)

    fun fetchStreamData(channels: List<String>, stringBuilder: (viewers: Int) -> String) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            timer(STREAM_REFRESH_RATE) {
                val data = mutableMapOf<String, String>()
                channels.forEach { channel ->
                    TwitchApi.getStream(channel)?.let {
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
        private const val STREAM_REFRESH_RATE = 30_000L
    }
}