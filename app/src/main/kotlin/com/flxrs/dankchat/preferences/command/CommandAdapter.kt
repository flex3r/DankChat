package com.flxrs.dankchat.preferences.command

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.databinding.CommandAddItemBinding
import com.flxrs.dankchat.databinding.CommandItemBinding

class CommandAdapter(val commands: MutableList<CommandItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class ItemViewHolder(val binding: CommandItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.commandDelete.setOnClickListener {
                commands.removeAt(bindingAdapterPosition)
                notifyItemRemoved(bindingAdapterPosition)
            }
        }
    }

    inner class AddViewHolder(binding: CommandAddItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.commandAdd.setOnClickListener {
                val command = CommandItem.Entry(trigger = "", command = "")
                val position = commands.size - 1
                commands.add(position, command)
                notifyItemInserted(position)
            }
        }
    }

    override fun getItemCount(): Int = commands.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_ENTRY -> ItemViewHolder(CommandItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            ITEM_VIEW_TYPE_ADD   -> AddViewHolder(CommandAddItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else                 -> throw ClassCastException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ItemViewHolder -> {
                val command = commands[position] as CommandItem.Entry
                holder.binding.command = command
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (commands[position]) {
            is CommandItem.Entry    -> ITEM_VIEW_TYPE_ENTRY
            is CommandItem.AddEntry -> ITEM_VIEW_TYPE_ADD
        }
    }

    companion object {
        private const val ITEM_VIEW_TYPE_ENTRY = 0
        private const val ITEM_VIEW_TYPE_ADD = 1
    }
}