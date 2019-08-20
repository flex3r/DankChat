package com.flxrs.dankchat.chat

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter

class ChatTabAdapter(supportFragmentManager: FragmentManager, lifecycle: Lifecycle) : FragmentStateAdapter(supportFragmentManager, lifecycle) {

    val titleList = mutableListOf<String>()
    val fragmentList = mutableListOf<ChatFragment>()

    override fun createFragment(position: Int): Fragment = fragmentList[position]

    override fun getItemCount(): Int = titleList.size

    override fun getItemId(position: Int): Long {
        return if (position < titleList.size) {
            titleList[position].hashCode().toLong()
        } else {
            RecyclerView.NO_ID
        }
    }

    override fun containsItem(itemId: Long): Boolean {
        titleList.forEach {
            if (it.hashCode().toLong() == itemId) return true
        }
        return false
    }

    fun addFragment(title: String) {
        titleList.add(title)
        fragmentList.add(ChatFragment.newInstance(title))
        notifyItemInserted(titleList.size - 1)
    }

    fun removeFragment(index: Int) {
        if (index < titleList.size) {
            titleList.removeAt(index)
            notifyItemRemoved(index)
            fragmentList.removeAt(index)
        }
    }
}