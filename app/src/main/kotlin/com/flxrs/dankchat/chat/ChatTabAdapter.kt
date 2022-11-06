package com.flxrs.dankchat.chat

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.flxrs.dankchat.preferences.DankChatPreferenceStore

class ChatTabAdapter(parentFragment: Fragment, private val dankChatPreferenceStore: DankChatPreferenceStore) : FragmentStateAdapter(parentFragment) {

    private val _channels = mutableListOf<String>()
    private val _channelsWithRenames = mutableListOf<String>()

    val channels: List<String> = _channels
    val channelsWithRenames: List<String> = _channelsWithRenames

    override fun createFragment(position: Int) = ChatFragment.newInstance(_channels[position])

    override fun getItemCount(): Int = _channels.size

    override fun getItemId(position: Int): Long = when {
        position < _channels.size -> _channels[position].hashCode().toLong()
        else                      -> RecyclerView.NO_ID
    }

    override fun containsItem(itemId: Long): Boolean = _channels.any { it.hashCode().toLong() == itemId }

    fun addFragment(channel: String) {
        _channels += channel
        _channelsWithRenames += dankChatPreferenceStore.getRenamedChannel(channel) ?: channel
        notifyItemInserted(_channels.lastIndex)
    }

    fun updateFragments(channels: List<String>) {
        val oldChannels = _channels.toList()
        val oldChannelsWithRenames = _channelsWithRenames.toList()
        val newChannelsWithRenames = channels.map { dankChatPreferenceStore.getRenamedChannel(it) ?: it }

        _channels.clear()
        _channels.addAll(channels)

        _channelsWithRenames.clear()
        _channelsWithRenames.addAll(newChannelsWithRenames)

        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldChannels.size
            override fun getNewListSize(): Int = channels.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldChannels[oldItemPosition] == channels[newItemPosition]
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldChannelsWithRenames[oldItemPosition] == newChannelsWithRenames[newItemPosition]
            }
        })
        result.dispatchUpdatesTo(this)
    }
}