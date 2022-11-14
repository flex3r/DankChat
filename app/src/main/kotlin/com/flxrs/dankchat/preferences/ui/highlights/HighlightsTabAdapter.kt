package com.flxrs.dankchat.preferences.ui.highlights

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.databinding.TabListBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore

class HighlightsTabAdapter(
    private val onAddItem: () -> Unit,
    private val onDeleteItem: (item: HighlightItem) -> Unit,
    private val preferences: DankChatPreferenceStore,
) : ListAdapter<HighlightsTabItem, HighlightsTabAdapter.ItemViewHolder>(DetectDiff()) {

    inner class ItemViewHolder(val adapter: HighlightsItemAdapter, val binding: TabListBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int = HighlightsTab.values().size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val adapter = HighlightsItemAdapter(onAddItem, onDeleteItem, preferences)
        val binding = TabListBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply {
            tabList.layoutManager = LinearLayoutManager(parent.context)
            tabList.adapter = adapter
        }
        return ItemViewHolder(adapter, binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val items = getItem(position).items
        with(holder) {
            adapter.submitList(items)
            if (items.size > adapter.itemCount) {
                binding.root.post {
                    binding.tabList.scrollToPosition(items.lastIndex)
                }
            }
        }
    }

    private class DetectDiff : DiffUtil.ItemCallback<HighlightsTabItem>() {
        override fun areItemsTheSame(oldItem: HighlightsTabItem, newItem: HighlightsTabItem): Boolean {
            return oldItem.tab == newItem.tab
        }

        override fun areContentsTheSame(oldItem: HighlightsTabItem, newItem: HighlightsTabItem): Boolean {
            return oldItem.items == newItem.items
        }
    }
}