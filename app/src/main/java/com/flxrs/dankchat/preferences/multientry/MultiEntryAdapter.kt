package com.flxrs.dankchat.preferences.multientry

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.databinding.MultiEntryAddItemBinding
import com.flxrs.dankchat.databinding.MultiEntryItemBinding

class MultiEntryAdapter(val entries: MutableList<MultiEntryItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class EntryViewHolder(val binding: MultiEntryItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.multiEntryDelete.setOnClickListener {
                entries.removeAt(adapterPosition)
                notifyItemRemoved(adapterPosition)
            }
        }
    }

    inner class AddViewHolder(val binding: MultiEntryAddItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.multiEntryAdd.setOnClickListener {
                val entry = MultiEntryItem.Entry("", false)
                entries.add(entries.size - 1, entry)
                notifyItemInserted(entries.size - 1)
            }
        }
    }

    override fun getItemCount(): Int {
        return entries.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_ENTRY -> EntryViewHolder(
                MultiEntryItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            ITEM_VIEW_TYPE_ADD -> AddViewHolder(
                MultiEntryAddItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            else -> throw ClassCastException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EntryViewHolder -> {
                val entry = entries[position] as MultiEntryItem.Entry
                holder.binding.entry = entry
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (entries[position]) {
            is MultiEntryItem.Entry -> ITEM_VIEW_TYPE_ENTRY
            is MultiEntryItem.AddEntry -> ITEM_VIEW_TYPE_ADD
        }
    }

    companion object {
        private const val ITEM_VIEW_TYPE_ENTRY = 0
        private const val ITEM_VIEW_TYPE_ADD = 1
    }
}