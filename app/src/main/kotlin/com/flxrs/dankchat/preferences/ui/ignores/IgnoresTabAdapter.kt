package com.flxrs.dankchat.preferences.ui.ignores

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.databinding.TabListBinding

class IgnoresTabAdapter(
    private val onAddItem: () -> Unit,
    private val onDeleteItem: (item: IgnoreItem) -> Unit,
) : ListAdapter<IgnoresTabItem, IgnoresTabAdapter.ItemViewHolder>(DetectDiff()) {

    inner class ItemViewHolder(val adapter: IgnoresItemAdapter, val binding: TabListBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int = IgnoresTab.values().size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val adapter = IgnoresItemAdapter(onAddItem, onDeleteItem)
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

    private class DetectDiff : DiffUtil.ItemCallback<IgnoresTabItem>() {
        override fun areItemsTheSame(oldItem: IgnoresTabItem, newItem: IgnoresTabItem): Boolean {
            return oldItem.tab == newItem.tab
        }

        override fun areContentsTheSame(oldItem: IgnoresTabItem, newItem: IgnoresTabItem): Boolean {
            return oldItem.items == newItem.items
        }
    }
}