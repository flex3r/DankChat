package com.flxrs.dankchat.chat.menu

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.databinding.EmoteHeaderItemBinding
import com.flxrs.dankchat.databinding.EmoteItemBinding
import java.util.*

class EmoteAdapter(private val onEmoteClick: (emote: String) -> Unit) : ListAdapter<EmoteItem, RecyclerView.ViewHolder>(DetectDiff()) {

    inner class ViewHolder(val binding: EmoteItemBinding) : RecyclerView.ViewHolder(binding.root)
    inner class TextViewHolder(val binding: EmoteHeaderItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_HEADER -> TextViewHolder(EmoteHeaderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            ITEM_VIEW_TYPE_ITEM -> ViewHolder(EmoteItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> throw ClassCastException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ViewHolder -> {
                val item = getItem(position) as EmoteItem.Emote
                TooltipCompat.setTooltipText(holder.binding.emoteView, item.emote.code)
                holder.binding.emote = item.emote
                holder.binding.root.setOnClickListener { onEmoteClick(item.emote.code) }
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
            is EmoteItem.Emote -> ITEM_VIEW_TYPE_ITEM
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