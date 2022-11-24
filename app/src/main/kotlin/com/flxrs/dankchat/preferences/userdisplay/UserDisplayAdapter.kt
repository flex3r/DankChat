package com.flxrs.dankchat.preferences.userdisplay

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.databinding.AddItemBinding
import com.flxrs.dankchat.databinding.UserDisplayItemBinding

class UserDisplayAdapter(val entries: MutableList<UserDisplayItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class EntryViewHolder(val binding: UserDisplayItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.userDelete.setOnClickListener {
                entries.removeAt(bindingAdapterPosition)
                notifyItemRemoved(bindingAdapterPosition)
            }
            (binding.userDisplayEnableColor).apply {
                setOnCheckedChangeListener { _, checked ->
                    val item = binding.userDisplay ?: return@setOnCheckedChangeListener
                    item.colorEnabled = checked
                    binding.userDisplayColorInput.isVisible = checked
                }
            }
            (binding.userDisplayEnableAlias).apply {
                setOnCheckedChangeListener { _, checked ->
                    val item = binding.userDisplay ?: return@setOnCheckedChangeListener
                    item.aliasEnabled = checked
                    binding.userDisplayAliasInput.isVisible = checked
                }
            }
        }
    }

    // stolen UI lule
    inner class AddItemViewHolder(val binding: AddItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.multiEntryAdd.setOnClickListener {
                // ID 0, so that the create call generate the ID
                val entry = UserDisplayItem.Entry(id = 0, username = "", colorHex = "#ff0000", alias = "")
                val index = entries.lastIndex
                entries.add(index, entry)
                notifyItemInserted(index)
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            0    -> EntryViewHolder(UserDisplayItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            1    -> AddItemViewHolder(AddItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> throw ClassCastException("Invalid view type $viewType")

        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EntryViewHolder -> {
                val entry = entries[position] as UserDisplayItem.Entry
                holder.binding.userDisplay = entry
                holder.binding.userDisplayEnableColor.isChecked = entry.colorEnabled
                holder.binding.userDisplayColorInput.isVisible = entry.colorEnabled
                holder.binding.userDisplayEnableAlias.isChecked = entry.aliasEnabled
                holder.binding.userDisplayAliasInput.isVisible = entry.aliasEnabled
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