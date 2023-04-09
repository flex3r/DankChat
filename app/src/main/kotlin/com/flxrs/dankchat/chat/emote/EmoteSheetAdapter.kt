package com.flxrs.dankchat.chat.emote

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.EmoteBottomsheetItemBinding
import com.flxrs.dankchat.utils.extensions.loadImage

class EmoteSheetAdapter(
    private val onUseClick: (item: EmoteSheetItem) -> Unit,
    private val onCopyClick: (item: EmoteSheetItem) -> Unit,
    private val onOpenLinkClick: (item: EmoteSheetItem) -> Unit,
    private val onImageClick: (item: EmoteSheetItem) -> Unit,
) : ListAdapter<EmoteSheetItem, EmoteSheetAdapter.ViewHolder>(DetectDiff()) {

    inner class ViewHolder(val binding: EmoteBottomsheetItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.emoteUse.setOnClickListener { onUseClick(getItem(bindingAdapterPosition)) }
            binding.emoteCopy.setOnClickListener { onCopyClick(getItem(bindingAdapterPosition)) }
            binding.emoteOpenLink.setOnClickListener { onOpenLinkClick(getItem(bindingAdapterPosition)) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = EmoteBottomsheetItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val emote = getItem(position)
        with(holder.binding) {
            emoteImage.loadImage(emote.imageUrl, placeholder = null, afterLoad = { emoteImageLoading.isVisible = false })
            emoteImage.setOnClickListener { onImageClick(emote) }
            emoteName.text = emote.name
            emoteType.text = buildString {
                append(root.context.getString(emote.emoteType))
                if (emote.isZeroWidth) {
                    append(" ")
                    append(root.context.getString(R.string.emote_sheet_zero_width_emote))
                }
            }
            when (emote.baseName) {
                null -> emoteBaseName.isVisible = false
                else -> {
                    emoteBaseName.isVisible = true
                    emoteBaseName.text = root.context.getString(R.string.emote_sheet_alias_of, emote.baseName)
                }
            }
            when (emote.creatorName) {
                null -> emoteCreator.isVisible = false
                else -> {
                    emoteCreator.isVisible = true
                    emoteCreator.text = root.context.getString(R.string.emote_sheet_created_by, emote.creatorName.value)
                }
            }
        }
    }

    private class DetectDiff : DiffUtil.ItemCallback<EmoteSheetItem>() {
        override fun areItemsTheSame(oldItem: EmoteSheetItem, newItem: EmoteSheetItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: EmoteSheetItem, newItem: EmoteSheetItem): Boolean = oldItem == newItem
    }
}
