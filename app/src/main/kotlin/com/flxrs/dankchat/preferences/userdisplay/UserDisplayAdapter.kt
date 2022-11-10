package com.flxrs.dankchat.preferences.userdisplay

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.databinding.MultiEntryAddItemBinding
import com.flxrs.dankchat.databinding.UserDisplayItemBinding

class UserDisplayAdapter(val entries: MutableList<UserDisplayItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class EntryViewHolder(val binding: UserDisplayItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.userDelete.setOnClickListener {
                entries.removeAt(bindingAdapterPosition)
                notifyItemRemoved(bindingAdapterPosition)
            }
        }
    }

    // stolen UI lule
    inner class AddItemViewHolder(val binding: MultiEntryAddItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.multiEntryAdd.setOnClickListener {
                val entry = UserDisplayItem.Entry(username = "", colorHex = "#ff0000")
                val index = entries.lastIndex
                entries.add(index, entry)
                notifyItemInserted(index)
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            0    -> EntryViewHolder(UserDisplayItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            1    -> AddItemViewHolder(MultiEntryAddItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> throw ClassCastException("Invalid view type $viewType")

        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EntryViewHolder -> {
                val entry = entries[position] as UserDisplayItem.Entry
                holder.binding.userDisplay = entry
            }
        }
    }

    override fun getItemCount(): Int = entries.size

    override fun getItemViewType(position: Int): Int {
        return when (entries[position]) {
            is UserDisplayItem.Entry -> ENTRY_VIEW_TYPE
            else                     -> ADD_ITEM_VIEW_TYPE
        }
    }

    companion object {
        private const val ENTRY_VIEW_TYPE = 0
        private const val ADD_ITEM_VIEW_TYPE = 1
    }

}