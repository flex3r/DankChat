package com.flxrs.dankchat.chat.emotemenu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.databinding.MenuTabListBinding
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent

class EmoteMenuAdapter(private val onEmoteClick: (emote: GenericEmote) -> Unit) : ListAdapter<EmoteMenuTabItem, EmoteMenuAdapter.ViewHolder>(DetectDiff()) {

    inner class ViewHolder(val adapter: EmoteAdapter, val binding: MenuTabListBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount() = EmoteMenuTab.entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val emoteAdapter = EmoteAdapter(onEmoteClick)
        return ViewHolder(emoteAdapter, MenuTabListBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply {
            tabList.apply {
                layoutManager = FlexboxLayoutManager(parent.context).apply {
                    justifyContent = JustifyContent.CENTER
                }
                adapter = emoteAdapter
            }
        })
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val emotes = getItem(position).items
        holder.adapter.submitList(emotes)
    }

    private class DetectDiff : DiffUtil.ItemCallback<EmoteMenuTabItem>() {
        override fun areItemsTheSame(oldItem: EmoteMenuTabItem, newItem: EmoteMenuTabItem): Boolean = oldItem.type == newItem.type
        override fun areContentsTheSame(oldItem: EmoteMenuTabItem, newItem: EmoteMenuTabItem): Boolean = oldItem == newItem
    }
}
