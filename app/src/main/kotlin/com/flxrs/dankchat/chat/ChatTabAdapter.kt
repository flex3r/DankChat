package com.flxrs.dankchat.chat

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.preferences.model.ChannelWithRename

class ChatTabAdapter(parentFragment: Fragment) : FragmentStateAdapter(parentFragment) {

    private val _channels = mutableListOf<ChannelWithRename>()

    override fun createFragment(position: Int) = ChatFragment.newInstance(_channels[position].channel)

    override fun getItemCount(): Int = _channels.size

    override fun getItemId(position: Int): Long = when (position) {
        in _channels.indices -> _channels[position].channel.hashCode().toLong()
        else                 -> RecyclerView.NO_ID
    }

    override fun containsItem(itemId: Long): Boolean = _channels.any { it.channel.hashCode().toLong() == itemId }

    fun indexOfChannel(channel: UserName): Int {
        return _channels.indexOfFirst { it.channel == channel }
    }

    operator fun get(position: Int): UserName? {
        if (position !in _channels.indices) return null
        return _channels[position].channel
    }

    fun getFormattedChannel(position: Int): String {
        val channel = _channels[position]
        return channel.rename?.value ?: channel.channel.value
    }

    fun addFragment(channel: UserName) {
        _channels += ChannelWithRename(channel, rename = null)
        notifyItemInserted(_channels.lastIndex)
    }

    fun updateFragments(channels: List<ChannelWithRename>) {
        val oldChannels = _channels.toList()
        _channels.clear()
        _channels.addAll(channels)

        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldChannels.size
            override fun getNewListSize(): Int = channels.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldChannels[oldItemPosition].channel == channels[newItemPosition].channel
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldChannels[oldItemPosition] == channels[newItemPosition]
            }
        })
        result.dispatchUpdatesTo(this)
    }
}
