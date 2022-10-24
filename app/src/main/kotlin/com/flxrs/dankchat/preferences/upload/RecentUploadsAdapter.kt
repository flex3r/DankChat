package com.flxrs.dankchat.preferences.upload

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import com.flxrs.dankchat.R
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

            copyButton.setOnClickListener {
                val clipData = ClipData.newPlainText("Image URL", item.imageUrl)
                val context = it.context
                context.getSystemService<ClipboardManager>()?.setPrimaryClip(clipData)
                // show copied toast only on Android < 13
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, context.getString(R.string.copied_image_url), Toast.LENGTH_SHORT).show()
                }
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