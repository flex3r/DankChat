package com.flxrs.dankchat.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.flxrs.dankchat.databinding.ChatItemBinding
import com.flxrs.dankchat.service.twitch.emote.EmoteManager

class ChatAdapter : ListAdapter<ChatItem, ChatAdapter.ViewHolder>(DetectDiff()) {

	var lastItemHeight = 0

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		return ViewHolder(ChatItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		EmoteManager.gifCallback.addView(holder.binding.itemText)
		holder.binding.chatItem = getItem(position)
		if (position == itemCount - 1) {
			lastItemHeight = holder.binding.itemText.measuredHeight
		}
	}

	override fun getItemId(position: Int): Long {
		return getItem(position).message.hashCode().toLong()
	}

	override fun onViewRecycled(holder: ViewHolder) {
		val view = holder.binding.itemText
		EmoteManager.gifCallback.removeView(view)
		holder.binding.chatItem = null
		holder.binding.executePendingBindings()
		Glide.with(view).clear(view)

		super.onViewRecycled(holder)
	}

	inner class ViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root)

	private class DetectDiff : DiffUtil.ItemCallback<ChatItem>() {
		override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
			return if ((!oldItem.historic && newItem.historic) || (!oldItem.message.timedOut && newItem.message.timedOut)) false else oldItem.message == newItem.message
		}

		override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
			return oldItem.message.timedOut == newItem.message.timedOut
					&& oldItem.message.name == newItem.message.name
					&& oldItem.message.channel == newItem.message.channel
					&& oldItem.message.message == newItem.message.message
					&& oldItem.message.isSystem == newItem.message.isSystem
					&& oldItem.message.isAction == newItem.message.isAction
					&& oldItem.message.time == newItem.message.time
		}
	}
}