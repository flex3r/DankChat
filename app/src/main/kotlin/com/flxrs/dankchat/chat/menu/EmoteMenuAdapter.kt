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
import com.flxrs.dankchat.databinding.EmojiMenuTabBinding
import com.flxrs.dankchat.databinding.MenuTabListBinding

class EmoteMenuAdapter(private val onEmoteClick: (emote: EmoteItem) -> Unit) : ListAdapter<EmoteMenuTabItem, RecyclerView.ViewHolder>(DetectDiff()) {

    inner class ViewHolder(val adapter: EmoteAdapter, binding: MenuTabListBinding) : RecyclerView.ViewHolder(binding.root)

    inner class EmojiViewHolder(binding: EmojiMenuTabBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount() = EmoteMenuTab.values().size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TAB_VIEW_TYPE_EMOJI -> {
                val binding = EmojiMenuTabBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply {
                    emojiPickerView.setOnEmojiPickedListener {
                        onEmoteClick(EmoteItem.Emoji(it.emoji))
                    }
                }
                EmojiViewHolder(binding)
            }

            else                -> {
                val emoteAdapter = EmoteAdapter(onEmoteClick)
                ViewHolder(emoteAdapter, MenuTabListBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply {
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
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).type) {
            EmoteMenuTab.EMOJI -> TAB_VIEW_TYPE_EMOJI
            else               -> TAB_VIEW_TYPE_DEFAULT
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ViewHolder      -> {
                val emotes = getItem(position).items
                holder.adapter.submitList(emotes)
            }
        }
    }

    companion object {
        private const val TAB_VIEW_TYPE_DEFAULT = 0
        private const val TAB_VIEW_TYPE_EMOJI = 1
    }

    private class DetectDiff : DiffUtil.ItemCallback<EmoteMenuTabItem>() {
        override fun areItemsTheSame(oldItem: EmoteMenuTabItem, newItem: EmoteMenuTabItem): Boolean = oldItem.type == newItem.type

        override fun areContentsTheSame(oldItem: EmoteMenuTabItem, newItem: EmoteMenuTabItem): Boolean = oldItem == newItem
    }
}
