package com.flxrs.dankchat.chat.mention

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.R
import com.flxrs.dankchat.chat.FullScreenSheetState
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.databinding.MentionFragmentBinding
import com.flxrs.dankchat.main.MainViewModel
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MentionFragment : Fragment() {

    private val mainViewModel: MainViewModel by viewModels({ requireParentFragment() })
    private val mentionViewModel: MentionViewModel by viewModels()
    private val args: MentionFragmentArgs by navArgs()
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
                    0    -> root.context.getString(R.string.mentions)
                    else -> root.context.getString(R.string.whispers)
                }
            }.apply { attach() }
        }

        mainViewModel.setSuggestionChannel(WhisperMessage.WHISPER_CHANNEL)
        mainViewModel.setFullScreenSheetState(binding.mentionTabs.selectedTabPosition.pageIndexToSheetState())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (savedInstanceState == null && args.openWhisperTab) {
            view.post {
                binding.mentionViewpager.setCurrentItem(1, true)
            }
        }

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

    fun scrollToWhisperTab() {
        binding.mentionViewpager.setCurrentItem(1, true)
    }

    private fun ViewPager2.setup() {
        adapter = tabAdapter
        offscreenPageLimit = 1
        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                mainViewModel.setFullScreenSheetState(position.pageIndexToSheetState())
                bindingRef?.mentionTabs?.getTabAt(position)?.removeBadge()
            }
        })
    }

    private fun Int.pageIndexToSheetState() = when (this) {
        0    -> FullScreenSheetState.Mention
        else -> FullScreenSheetState.Whisper
    }

    companion object {
        private val TAG = MentionFragment::class.java.simpleName

        fun newInstance(openWhisperTab: Boolean = false) = MentionFragment().apply {
            arguments = MentionFragmentArgs(openWhisperTab).toBundle()
        }
    }
}
