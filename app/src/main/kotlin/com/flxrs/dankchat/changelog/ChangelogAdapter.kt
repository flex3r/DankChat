package com.flxrs.dankchat.changelog

import android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.buildSpannedString
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.databinding.ChangelogItemBinding
import com.flxrs.dankchat.utils.extensions.px
import com.flxrs.dankchat.utils.span.ImprovedBulletSpan

class ChangelogAdapter : ListAdapter<String, ChangelogAdapter.ViewHolder>(DetectDiff()) {

    inner class ViewHolder(val binding: ChangelogItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ChangelogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.changelogEntry.text = buildSpannedString {
            val item = getItem(position)
            append(item, ImprovedBulletSpan(gapWidth = 8.px, bulletRadius = 3.px), SPAN_INCLUSIVE_EXCLUSIVE)
        }
    }

    private class DetectDiff : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}
