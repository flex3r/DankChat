package com.flxrs.dankchat

import androidx.lifecycle.*
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.service.TwitchRepository
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

    val appbarEnabled = MutableLiveData<Boolean>(true)
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
            twitchRepository.loadData(channel, oauth, id, load3rdParty, reAuth)
        }
    }

    fun setActiveChannel(channel: String) {
        activeChannel.value = channel
    }

    fun setStreamData(data: Map<String, String>) {
        streamData.value = data
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

    private fun buildBottomText(): String {
        val roomState = if (roomStateEnabled.value == true) {
            roomState.value?.toString() ?: ""
        } else ""
        val streamInfo = if (streamInfoEnabled.value == true) {
            currentStreamInformation.value ?: ""
        } else ""

        val stateNotBlank = roomState.isNotBlank()
        val streamNotBlank = streamInfo.isNotBlank()

        return when {
            stateNotBlank && streamNotBlank -> "$roomState - $streamInfo"
            stateNotBlank -> roomState
            streamNotBlank -> streamInfo
            else -> ""
        }
    }
}