package com.flxrs.dankchat.preferences.ui.tts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.databinding.TtsIgnoreListAddItemBinding
import com.flxrs.dankchat.databinding.TtsIgnoreListItemBinding

class TtsIgnoreListAdapter(val items: MutableList<TtsIgnoreItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class ItemViewHolder(val binding: TtsIgnoreListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.userDelete.setOnClickListener {
                items.removeAt(bindingAdapterPosition)
                notifyItemRemoved(bindingAdapterPosition)
            }
        }
    }

    inner class AddViewHolder(binding: TtsIgnoreListAddItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.userAdd.setOnClickListener {
                val item = TtsIgnoreItem.Entry(user = "")
                val position = items.lastIndex
                items.add(position, item)
                notifyItemInserted(position)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_ENTRY -> ItemViewHolder(TtsIgnoreListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            ITEM_VIEW_TYPE_ADD   -> AddViewHolder(TtsIgnoreListAddItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else                 -> throw ClassCastException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ItemViewHolder -> {
                val item = items[position] as TtsIgnoreItem.Entry
                holder.binding.item = item
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TtsIgnoreItem.Entry    -> ITEM_VIEW_TYPE_ENTRY
            is TtsIgnoreItem.AddEntry -> ITEM_VIEW_TYPE_ADD
        }
    }

    companion object {
        private const val ITEM_VIEW_TYPE_ENTRY = 0
        private const val ITEM_VIEW_TYPE_ADD = 1
    }
}
