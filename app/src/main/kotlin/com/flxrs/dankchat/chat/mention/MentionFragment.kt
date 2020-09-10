package com.flxrs.dankchat.chat.mention

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.MentionFragmentBinding
import com.google.android.material.tabs.TabLayoutMediator

class MentionFragment : Fragment() {

    private lateinit var tabAdapter: MentionTabAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private val dankChatViewModel: DankChatViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        tabAdapter = MentionTabAdapter(this)
        return MentionFragmentBinding.inflate(inflater, container, false).apply {
            mentionsToolbar.setNavigationOnClickListener { activity?.onBackPressed() }
            mentionViewpager.setup()
            tabLayoutMediator = TabLayoutMediator(mentionTabs, mentionViewpager) { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.mentions)
                    else -> getString(R.string.whispers)
                }
            }.apply { attach() }
        }.root
    }

    private fun ViewPager2.setup() {
        adapter = tabAdapter
        offscreenPageLimit = 2
        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                dankChatViewModel.setWhisperTabSelected(position == 1)
            }
        })
    }

    companion object {
        private val TAG = MentionFragment::class.java.simpleName
    }
}