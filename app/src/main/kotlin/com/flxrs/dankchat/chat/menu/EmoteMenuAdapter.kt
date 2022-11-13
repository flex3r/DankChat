package com.flxrs.dankchat.chat.menu

import android.content.res.Configuration
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.databinding.MenuTabListBinding

class EmoteMenuAdapter(private val onEmoteClick: (emote: GenericEmote) -> Unit) : ListAdapter<EmoteMenuTabItem, EmoteMenuAdapter.ViewHolder>(DetectDiff()) {

    inner class ViewHolder(val adapter: EmoteAdapter, binding: MenuTabListBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount() = EmoteMenuTab.values().size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val emoteAdapter = EmoteAdapter(onEmoteClick)
        return ViewHolder(emoteAdapter, MenuTabListBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply {
            val isLandscape = parent.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val spanCount = if (isLandscape) 12 else 6
            tabList.apply {
                layoutManager = GridLayoutManager(parent.context, spanCount).apply {
                    spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return when (emoteAdapter.getItemViewType(position)) {
                                EmoteAdapter.ITEM_VIEW_TYPE_HEADER -> spanCount
                                else                               -> 1
                            }
                        }
                    }
                }
                adapter = emoteAdapter
                addItemDecoration(object : RecyclerView.ItemDecoration() {
                    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                        outRect.setEmpty()
                    }
                })
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