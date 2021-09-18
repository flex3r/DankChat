package com.flxrs.dankchat.channels

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChannelsItemBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ChannelsAdapter(private val onEditChannel: (String, String?) -> Unit) : ListAdapter<String, ChannelsAdapter.ChannelViewHolder>(DetectDiff()) {
    class ChannelViewHolder(val binding: ChannelsItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        return ChannelViewHolder(ChannelsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) = with(holder.binding) {
        val dankChatPreferences = DankChatPreferenceStore(holder.itemView.context)
        val channel = getItem(position)
        val renamedChannel = dankChatPreferences.getRenamedChannel(channel)
        channelText.text = renamedChannel ?: channel
        channelDelete.setOnClickListener {
            MaterialAlertDialogBuilder(root.context)
                .setTitle(R.string.confirm_channel_removal_title)
                .setMessage(R.string.confirm_channel_removal_message)
                .setPositiveButton(R.string.confirm_channel_removal_positive_button) { dialog, _ ->
                    currentList.toMutableList().let {
                        it.removeAt(holder.bindingAdapterPosition)
                        submitList(it)
                    }
                    dankChatPreferences.removeChannelRename(channel)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
                .create().show()
        }
        channelEdit.setOnClickListener{
            val currentChannel = currentList.getOrNull(holder.bindingAdapterPosition) ?: return@setOnClickListener
            onEditChannel(currentChannel, renamedChannel)
        }
    }
}

private class DetectDiff : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
}