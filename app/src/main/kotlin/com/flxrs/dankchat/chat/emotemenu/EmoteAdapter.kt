package com.flxrs.dankchat.chat.emotemenu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.databinding.EmoteHeaderItemBinding
import com.flxrs.dankchat.databinding.EmoteItemBinding
import com.flxrs.dankchat.utils.extensions.loadImage
import java.util.Locale

class EmoteAdapter(private val onEmoteClick: (emote: GenericEmote) -> Unit) : ListAdapter<EmoteItem, RecyclerView.ViewHolder>(DetectDiff()) {

    inner class ViewHolder(val binding: EmoteItemBinding) : RecyclerView.ViewHolder(binding.root)
    inner class TextViewHolder(val binding: EmoteHeaderItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_HEADER -> TextViewHolder(EmoteHeaderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            ITEM_VIEW_TYPE_ITEM   -> ViewHolder(EmoteItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else                  -> throw ClassCastException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ViewHolder     -> {
                val emoteItem = getItem(position) as EmoteItem.Emote
                val emote = emoteItem.emote
                holder.binding.root.setOnClickListener { onEmoteClick(emote) }
                with(holder.binding.emoteView) {
                    TooltipCompat.setTooltipText(this, emote.code)
                    contentDescription = emote.code
                    loadImage(emote.lowResUrl)
                }
            }

            is TextViewHolder -> {
                val item = getItem(position) as EmoteItem.Header
                holder.binding.text.text = item.title.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is EmoteItem.Header -> ITEM_VIEW_TYPE_HEADER
            is EmoteItem.Emote  -> ITEM_VIEW_TYPE_ITEM
        }
    }

    private class DetectDiff : DiffUtil.ItemCallback<EmoteItem>() {
        override fun areItemsTheSame(oldItem: EmoteItem, newItem: EmoteItem): Boolean = oldItem == newItem

        override fun areContentsTheSame(oldItem: EmoteItem, newItem: EmoteItem): Boolean = oldItem == newItem
    }

    companion object {
        const val ITEM_VIEW_TYPE_HEADER = 0
        const val ITEM_VIEW_TYPE_ITEM = 1
    }
}
