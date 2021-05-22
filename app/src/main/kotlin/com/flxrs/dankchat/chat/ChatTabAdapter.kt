package com.flxrs.dankchat.chat

import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter

class ChatTabAdapter(parentFragment: Fragment) : FragmentStateAdapter(parentFragment) {

    val titleList = mutableListOf<String>()
    //val fragmentList = mutableListOf<ChatFragment>()

    override fun createFragment(position: Int) = ChatFragment.newInstance(titleList[position])

    override fun getItemCount(): Int = titleList.size

    override fun getItemId(position: Int): Long = when {
        position < titleList.size -> titleList[position].hashCode().toLong()
        else -> RecyclerView.NO_ID
    }

    override fun containsItem(itemId: Long): Boolean {
        titleList.forEach {
            if (it.hashCode().toLong() == itemId) return true
        }
        return false
    }

    fun addFragment(title: String) {
        titleList += title
        notifyItemInserted(titleList.lastIndex)
    }

    fun removeFragment(index: Int) {
        if (index < titleList.size) {
            titleList.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateFragments(titles: List<String>) {
        titleList.clear()
        titleList.addAll(titles)
        notifyDataSetChanged()
    }
}