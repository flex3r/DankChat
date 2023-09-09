package com.flxrs.dankchat.channels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.italic
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChannelsItemBinding
import com.flxrs.dankchat.preferences.model.ChannelWithRename
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ChannelsAdapter(
    private val dankChatPreferences: DankChatPreferenceStore,
    private val onEditChannel: (ChannelWithRename) -> Unit
) : ListAdapter<ChannelWithRename, ChannelsAdapter.ChannelViewHolder>(DetectDiff()) {
    class ChannelViewHolder(val binding: ChannelsItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        return ChannelViewHolder(ChannelsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) = with(holder.binding) {
        val (channel, rename) = getItem(position)
        channelText.text = buildSpannedString {
            append(rename?.value ?: channel.value)
            rename
                ?.takeIf { it != channel }
                ?.let {
                    val channelColor = ColorUtils.setAlphaComponent(channelText.currentTextColor, 128)
                    color(channelColor) { italic { append(" $channel") } }
                }
        }
        channelDelete.setOnClickListener {
            MaterialAlertDialogBuilder(root.context)
                .setTitle(R.string.confirm_channel_removal_title)
                .setMessage(R.string.confirm_channel_removal_message)
                .setPositiveButton(R.string.confirm_channel_removal_positive_button) { dialog, _ ->
                    dankChatPreferences.removeChannel(channel)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
                .create().show()
        }
        channelEdit.setOnClickListener {
            val editedChannel = currentList.getOrNull(holder.bindingAdapterPosition) ?: return@setOnClickListener
            onEditChannel(editedChannel)
        }
    }
}

private class DetectDiff : DiffUtil.ItemCallback<ChannelWithRename>() {
    override fun areItemsTheSame(oldItem: ChannelWithRename, newItem: ChannelWithRename): Boolean = oldItem.channel == newItem.channel
    override fun areContentsTheSame(oldItem: ChannelWithRename, newItem: ChannelWithRename): Boolean = oldItem == newItem
}
