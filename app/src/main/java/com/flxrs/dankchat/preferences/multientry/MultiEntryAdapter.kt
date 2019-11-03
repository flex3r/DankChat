package com.flxrs.dankchat.preferences.multientry

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.databinding.MultiEntryAddItemBinding
import com.flxrs.dankchat.databinding.MultiEntryItemBinding

class MultiEntryAdapter : ListAdapter<MultiEntryItem, RecyclerView.ViewHolder>(DetectDiff()) {

    inner class EntryViewHolder(val binding: MultiEntryItemBinding) : RecyclerView.ViewHolder(binding.root)
    inner class AddViewHolder(val binding: MultiEntryAddItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_ENTRY -> EntryViewHolder(
                MultiEntryItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            ITEM_VIEW_TYPE_ADD   -> AddViewHolder(
                MultiEntryAddItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            else                 -> throw ClassCastException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EntryViewHolder -> {
                val entry = getItem(position) as MultiEntryItem.Entry
                holder.binding.entry = entry
                holder.binding.multiEntryDelete.setOnClickListener {
                    submitList(currentList.minus(entry))
                }
            }
            is AddViewHolder   -> {
                holder.binding.multiEntryAdd.setOnClickListener {
                    val entry = MultiEntryItem.Entry("", false)
                    submitList(currentList.dropLast(1).plus(entry).plus(currentList.last()))
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is MultiEntryItem.Entry    -> ITEM_VIEW_TYPE_ENTRY
            is MultiEntryItem.AddEntry -> ITEM_VIEW_TYPE_ADD
        }
    }

    private class DetectDiff : DiffUtil.ItemCallback<MultiEntryItem>() {

        override fun areItemsTheSame(oldItem: MultiEntryItem, newItem: MultiEntryItem): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: MultiEntryItem, newItem: MultiEntryItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val ITEM_VIEW_TYPE_ENTRY = 0
        private const val ITEM_VIEW_TYPE_ADD = 1
    }
}