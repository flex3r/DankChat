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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MentionFragment : Fragment() {

    private val dankChatViewModel: DankChatViewModel by activityViewModels()
    private var bindingRef: MentionFragmentBinding? = null
    private val binding get() = bindingRef!!
    private lateinit var tabAdapter: MentionTabAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        tabAdapter = MentionTabAdapter(this)
        bindingRef = MentionFragmentBinding.inflate(inflater, container, false).apply {
            mentionsToolbar.setNavigationOnClickListener { activity?.onBackPressed() }
            mentionViewpager.setup()
            tabLayoutMediator = TabLayoutMediator(mentionTabs, mentionViewpager) { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.mentions)
                    else -> getString(R.string.whispers)
                }
            }.apply { attach() }
        }

        dankChatViewModel.apply {
            hasMentions.observe(viewLifecycleOwner) {
                when {
                    it -> if (binding.mentionTabs.selectedTabPosition != 0) {
                        binding.mentionTabs.getTabAt(0)?.apply { orCreateBadge }
                    }
                    else -> binding.mentionTabs.getTabAt(0)?.removeBadge()
                }
            }
            hasWhispers.observe(viewLifecycleOwner) {
                if (it && binding.mentionTabs.selectedTabPosition != 1) {
                    binding.mentionTabs.getTabAt(1)?.apply { orCreateBadge }
                }
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingRef = null
    }

    private fun ViewPager2.setup() {
        adapter = tabAdapter
        offscreenPageLimit = 2
        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                dankChatViewModel.setWhisperTabSelected(position == 1)
                bindingRef?.mentionTabs?.getTabAt(position)?.removeBadge()
            }
        })
    }

    companion object {
        private val TAG = MentionFragment::class.java.simpleName
    }
}