package com.flxrs.dankchat.chat.mention

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.MentionFragmentBinding
import com.flxrs.dankchat.main.MainViewModel
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MentionFragment : Fragment() {

    private val mainViewModel: MainViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )
    private val mentionViewModel: MentionViewModel by viewModels()
    private var bindingRef: MentionFragmentBinding? = null
    private val binding get() = bindingRef!!
    private lateinit var tabAdapter: MentionTabAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        tabAdapter = MentionTabAdapter(this)
        bindingRef = MentionFragmentBinding.inflate(inflater, container, false).apply {
            mentionsToolbar.setNavigationOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }
            mentionViewpager.setup()
            tabLayoutMediator = TabLayoutMediator(mentionTabs, mentionViewpager) { tab, position ->
                tab.text = when (position) {
                    0    -> getString(R.string.mentions)
                    else -> getString(R.string.whispers)
                }
            }.apply { attach() }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mentionViewModel.apply {
            collectFlow(hasMentions) {
                when {
                    it   -> if (binding.mentionTabs.selectedTabPosition != 0) {
                        binding.mentionTabs.getTabAt(0)?.apply { orCreateBadge }
                    }

                    else -> binding.mentionTabs.getTabAt(0)?.removeBadge()
                }
            }
            collectFlow(hasWhispers) {
                if (it && binding.mentionTabs.selectedTabPosition != 1) {
                    binding.mentionTabs.getTabAt(1)?.apply { orCreateBadge }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.mentionViewpager.adapter = null
        tabLayoutMediator.detach()
        bindingRef = null
        super.onDestroyView()
    }

    private fun ViewPager2.setup() {
        adapter = tabAdapter
        offscreenPageLimit = 2
        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                mainViewModel.setWhisperTabSelected(position == 1)
                bindingRef?.mentionTabs?.getTabAt(position)?.removeBadge()
            }
        })
    }

    companion object {
        private val TAG = MentionFragment::class.java.simpleName
    }
}
