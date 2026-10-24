package com.flxrs.dankchat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/** One [ViewModelStore] per channel, cleared when the channel is removed so a re-added channel gets fresh view models. */
@KoinViewModel
class ChannelViewModelStores(
    chatChannelProvider: ChatChannelProvider,
) : ViewModel() {
    private val owners = mutableMapOf<UserName, ViewModelStoreOwner>()

    init {
        viewModelScope.launch {
            chatChannelProvider.channels.collect { channels ->
                val current = channels.orEmpty().toSet()
                val removed = owners.keys - current
                removed.forEach { channel ->
                    owners.remove(channel)?.viewModelStore?.clear()
                }
            }
        }
    }

    fun ownerFor(channel: UserName): ViewModelStoreOwner = owners.getOrPut(channel) {
        val store = ViewModelStore()
        object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
    }

    override fun onCleared() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
    }
}
