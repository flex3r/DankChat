package com.flxrs.dankchat.chat

import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
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

    @SuppressLint("NotifyDataSetChanged")
    fun updateFragments(titles: List<String>) {
        _titleList.clear()
        _titleList.addAll(titles)
        notifyDataSetChanged()
    }
}