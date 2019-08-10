package com.flxrs.dankchat

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.service.TwitchRepository
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.service.twitch.message.TwitchMessage
import com.flxrs.dankchat.utils.SingleLiveEvent
import java.io.File

class DankChatViewModel(private val twitchRepository: TwitchRepository) : ViewModel() {

    val imageUploadedEvent: SingleLiveEvent<Pair<String, File>> = twitchRepository.imageUploadedEvent

    fun getChat(channel: String): LiveData<List<ChatItem>> = twitchRepository.getChat(channel)

    fun getCanType(channel: String): LiveData<String> = twitchRepository.getCanType(channel)

    fun getEmoteKeywords(channel: String): LiveData<List<GenericEmote>> = twitchRepository.getEmoteKeywords(channel)

    fun getRoomState(channel: String): LiveData<TwitchMessage.Roomstate> = twitchRepository.getRoomState(channel)

    fun loadData(channel: String, oauth: String, id: Int, load3rdParty: Boolean, reAuth: Boolean) {
        if (channel.isNotBlank()) {
            twitchRepository.loadData(channel, oauth, id, load3rdParty, reAuth)
        }
    }

    fun removeChannelData(channel: String) = twitchRepository.removeChannelData(channel)

    fun clear(channel: String) = twitchRepository.clear(channel)

    fun reloadEmotes(channel: String, oauth: String, id: Int) = twitchRepository.reloadEmotes(channel, oauth, id)

    fun uploadImage(file: File) = twitchRepository.uploadImage(file)
}