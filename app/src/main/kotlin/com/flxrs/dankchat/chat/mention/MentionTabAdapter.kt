package com.flxrs.dankchat.chat.mention

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MentionTabAdapter(parentFragment: Fragment) : FragmentStateAdapter(parentFragment) {

    override fun getItemCount(): Int = NUM_TABS
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> MentionChatFragment.newInstance()
        else -> MentionChatFragment.newInstance(isWhisperTab = true)
    }

    companion object {
        const val NUM_TABS = 2
    }
}