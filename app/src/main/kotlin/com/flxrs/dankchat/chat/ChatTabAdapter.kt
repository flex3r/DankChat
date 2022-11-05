package com.flxrs.dankchat.chat

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter

class ChatTabAdapter(parentFragment: Fragment) : FragmentStateAdapter(parentFragment) {

    private val _titleList = mutableListOf<String>()
    val titleList: List<String> = _titleList

    override fun createFragment(position: Int) = ChatFragment.newInstance(_titleList[position])

    override fun getItemCount(): Int = _titleList.size

    override fun getItemId(position: Int): Long = when {
        position < _titleList.size -> _titleList[position].hashCode().toLong()
        else                       -> RecyclerView.NO_ID
    }

    override fun containsItem(itemId: Long): Boolean = _titleList.any { it.hashCode().toLong() == itemId }

    fun addFragment(title: String) {
        _titleList += title
        notifyItemInserted(_titleList.lastIndex)
    }

    fun updateFragments(titles: List<String>) {
        if (titles == _titleList) {
            // nothing to do
            return
        }

        val oldList = _titleList.toList()
        _titleList.clear()
        _titleList.addAll(titles)

        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = titles.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition] == titles[newItemPosition]
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition] == titles[newItemPosition]
            }
        })
        result.dispatchUpdatesTo(this)
    }
}