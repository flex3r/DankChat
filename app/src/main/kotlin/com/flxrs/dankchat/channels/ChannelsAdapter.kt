package com.flxrs.dankchat.channels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChannelsItemBinding
import com.flxrs.dankchat.utils.extensions.assign
import com.flxrs.dankchat.utils.extensions.mutableSharedFlowOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ChannelsAdapter : ListAdapter<String, ChannelsAdapter.ChannelViewHolder>(DetectDiff()) {

    private val _rename = mutableSharedFlowOf(mutableMapOf<String,String>())
    private val rename: SharedFlow<Map<String,String>> = _rename.asSharedFlow()

    class ChannelViewHolder(val binding: ChannelsItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        return ChannelViewHolder(ChannelsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) = with(holder.binding) {
        channelText.text = getItem(position)
        channelDelete.setOnClickListener {

            MaterialAlertDialogBuilder(root.context)
                .setTitle(R.string.confirm_channel_removal_title)
                .setMessage(R.string.confirm_channel_removal_message)
                .setPositiveButton(R.string.confirm_channel_removal_positive_button) { dialog, _ ->
                    currentList.toMutableList().let {
                        it.removeAt(holder.bindingAdapterPosition)
                        submitList(it)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
                .create().show()
        }

        channelEdit.setOnClickListener{
            _rename.assign("channel", currentList[holder.bindingAdapterPosition])
        }

    }

    fun getRename(): SharedFlow<Map<String,String>>{
        return rename
    }
}

private class DetectDiff : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
}