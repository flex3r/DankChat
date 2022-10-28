package com.flxrs.dankchat.preferences.ui.highlights

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.databinding.HighlightsTabListBinding

class HighlightsMenuAdapter(
    private val addItem: () -> Unit,
    private val deleteItem: (item: HighlightItem) -> Unit,
) : ListAdapter<HighlightsTabItem, HighlightsMenuAdapter.ItemViewHolder>(DetectDiff()) {

    inner class ItemViewHolder(val adapter: HighlightsItemAdapter, binding: HighlightsTabListBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int = HighlightsMenuTab.values().size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val adapter = HighlightsItemAdapter(addItem, deleteItem)
        val binding = HighlightsTabListBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply {
            tabList.layoutManager = LinearLayoutManager(parent.context)
            tabList.adapter = adapter
        }
        return ItemViewHolder(adapter, binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val items = getItem(position).items
        Log.d("HighlightsMenuAdapter", "Binding items: $items")
        holder.adapter.submitList(items)
    }

    private class DetectDiff : DiffUtil.ItemCallback<HighlightsTabItem>() {
        override fun areItemsTheSame(oldItem: HighlightsTabItem, newItem: HighlightsTabItem): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: HighlightsTabItem, newItem: HighlightsTabItem): Boolean {
            return oldItem.items == newItem.items
        }
    }
}