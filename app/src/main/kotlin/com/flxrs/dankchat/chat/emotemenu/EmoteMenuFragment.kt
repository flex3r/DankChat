package com.flxrs.dankchat.chat.emotemenu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.EmoteMenuFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.main.MainViewModel
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.google.android.material.tabs.TabLayoutMediator

class EmoteMenuFragment : Fragment() {

    private val mainViewModel: MainViewModel by viewModels({ requireParentFragment() })
    private var bindingRef: EmoteMenuFragmentBinding? = null
    private val binding get() = bindingRef!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val adapter = EmoteMenuAdapter {
            (parentFragment as? MainFragment)?.insertEmote(it.code, it.id)
        }
        bindingRef = EmoteMenuFragmentBinding.inflate(inflater, container, false).apply {
            bottomSheetViewPager.adapter = adapter
            bottomSheetViewPager.updateLayoutParams {
                height = (resources.displayMetrics.heightPixels * HEIGHT_SCALE_FACTOR).toInt()
            }

            TabLayoutMediator(bottomSheetTabs, bottomSheetViewPager) { tab, pos ->
                val menuTab = EmoteMenuTab.values()[pos]
                tab.text = when (menuTab) {
                    EmoteMenuTab.SUBS    -> root.context.getString(R.string.emote_menu_tab_subs)
                    EmoteMenuTab.CHANNEL -> root.context.getString(R.string.emote_menu_tab_channel)
                    EmoteMenuTab.GLOBAL  -> root.context.getString(R.string.emote_menu_tab_global)
                    EmoteMenuTab.RECENT  -> root.context.getString(R.string.emote_menu_tab_recent)
                }
            }.attach()
        }

        collectFlow(mainViewModel.emoteTabItems, adapter::submitList)
        mainViewModel.setEmoteInputSheetState()
        return binding.root
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    companion object {
        private const val HEIGHT_SCALE_FACTOR = 0.4
    }
}
