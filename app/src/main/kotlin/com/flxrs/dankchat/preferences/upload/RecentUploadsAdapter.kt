package com.flxrs.dankchat.preferences.upload

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import com.flxrs.dankchat.databinding.RecentUploadItemBinding

class RecentUploadsAdapter : ListAdapter<RecentUpload, RecentUploadsAdapter.UploadViewHolder>(DetectDiff()) {

    inner class UploadViewHolder(val binding: RecentUploadItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UploadViewHolder {
        return UploadViewHolder(RecentUploadItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: UploadViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            upload = item
            image.load(item.imageUrl) {
                scale(Scale.FILL)
            }
        }
    }

}

private class DetectDiff : DiffUtil.ItemCallback<RecentUpload>() {
    override fun areItemsTheSame(oldItem: RecentUpload, newItem: RecentUpload): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: RecentUpload, newItem: RecentUpload): Boolean {
        return oldItem == newItem
    }
}