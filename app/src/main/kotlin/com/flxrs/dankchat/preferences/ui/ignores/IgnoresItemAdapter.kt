package com.flxrs.dankchat.preferences.ui.ignores

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.databinding.AddItemBinding
import com.flxrs.dankchat.databinding.MessageIgnoreItemBinding
import com.flxrs.dankchat.databinding.TwitchBlockItemBinding
import com.flxrs.dankchat.databinding.UserIgnoreItemBinding

class IgnoresItemAdapter(
    private val onAddItem: () -> Unit,
    private val onDeleteItem: (item: IgnoreItem) -> Unit
) : ListAdapter<IgnoreItem, RecyclerView.ViewHolder>(DetectDiff()) {

    inner class MessageItemViewHolder(val binding: MessageIgnoreItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.delete.setOnClickListener { onDeleteItem(getItem(bindingAdapterPosition)) }
            binding.isBlockMessage.setOnCheckedChangeListener { _, isChecked ->
                val item = binding.item ?: return@setOnCheckedChangeListener
                item.isBlockMessage = isChecked
                binding.replacement.isVisible = !isChecked
            }
        }
    }

    inner class UserItemViewHolder(val binding: UserIgnoreItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.delete.setOnClickListener { onDeleteItem(getItem(bindingAdapterPosition)) }
        }
    }

    inner class TwitchItemViewHolder(val binding: TwitchBlockItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.delete.setOnClickListener { onDeleteItem(getItem(bindingAdapterPosition)) }
        }
    }

    inner class AddViewHolder(binding: AddItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.multiEntryAdd.setOnClickListener { onAddItem() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_MESSAGE -> MessageItemViewHolder(MessageIgnoreItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            ITEM_VIEW_TYPE_USER    -> UserItemViewHolder(UserIgnoreItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            ITEM_VIEW_TYPE_TWITCH  -> TwitchItemViewHolder(TwitchBlockItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            ITEM_VIEW_TYPE_ADD     -> AddViewHolder(AddItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else                   -> throw ClassCastException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MessageItemViewHolder -> {
                val messageItem = getItem(position) as MessageIgnoreItem
                with(holder.binding) {
                    item = messageItem
                    isBlockMessage.isChecked = messageItem.isBlockMessage
                    replacement.isVisible = !messageItem.isBlockMessage
                }
            }

            is UserItemViewHolder    -> {
                val userItem = getItem(position) as UserIgnoreItem
                holder.binding.item = userItem
            }

            is TwitchItemViewHolder  -> {
                val twitchItem = getItem(position) as TwitchBlockItem
                holder.binding.item = twitchItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is MessageIgnoreItem -> ITEM_VIEW_TYPE_MESSAGE
            is UserIgnoreItem    -> ITEM_VIEW_TYPE_USER
            is TwitchBlockItem   -> ITEM_VIEW_TYPE_TWITCH
            is AddItem           -> ITEM_VIEW_TYPE_ADD
        }
    }

    private class DetectDiff : DiffUtil.ItemCallback<IgnoreItem>() {
        override fun areItemsTheSame(oldItem: IgnoreItem, newItem: IgnoreItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: IgnoreItem, newItem: IgnoreItem): Boolean = oldItem == newItem
    }

    companion object {
        private const val ITEM_VIEW_TYPE_MESSAGE = 0
        private const val ITEM_VIEW_TYPE_USER = 1
        private const val ITEM_VIEW_TYPE_TWITCH = 2
        private const val ITEM_VIEW_TYPE_ADD = 3
    }
}