package com.flxrs.dankchat.chat.emote

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.EmoteBottomsheetBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.utils.extensions.disableNestedScrolling
import com.flxrs.dankchat.utils.extensions.forEachViewHolder
import com.flxrs.dankchat.utils.extensions.isLandscape
import com.flxrs.dankchat.utils.extensions.recyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayoutMediator

class EmoteSheetFragment : BottomSheetDialogFragment() {

    private val viewModel: EmoteSheetViewModel by viewModels()
    private var bindingRef: EmoteBottomsheetBinding? = null
    private val binding get() = bindingRef!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val adapter = EmoteSheetAdapter(
            onUseClick = { sendResultAndDismiss(EmoteSheetResult.Use(it.name, it.id)) },
            onCopyClick = { sendResultAndDismiss(EmoteSheetResult.Copy(it.name)) },
            onOpenLinkClick = { emote ->
                Intent(Intent.ACTION_VIEW).also {
                    it.data = emote.providerUrl.toUri()
                    startActivity(it)
                }
            },
            onImageClick = { emote ->
                Intent(Intent.ACTION_VIEW).also {
                    it.data = emote.imageUrl.toUri()
                    startActivity(it)
                }
            },
        )
        val items = viewModel.items
        adapter.submitList(items)

        bindingRef = EmoteBottomsheetBinding.inflate(inflater, container, false).apply {
            emoteSheetViewPager.adapter = adapter
            TabLayoutMediator(emoteSheetTabs, emoteSheetViewPager) { tab, pos ->
                tab.text = items[pos].name
            }.attach()

            emoteSheetTabs.isVisible = items.size > 1
            emoteSheetViewPager.isUserInputEnabled = items.size > 1

            emoteSheetViewPager.disableNestedScrolling()
            emoteSheetViewPager.registerOnPageChangeCallback(emoteSheetPageChangeCallback)
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        dialog?.takeIf { isLandscape }?.let {
            with(it as BottomSheetDialog) {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }

    override fun onDestroyView() {
        binding.emoteSheetViewPager.unregisterOnPageChangeCallback(emoteSheetPageChangeCallback)
        bindingRef = null
        super.onDestroyView()
    }

    private val emoteSheetPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val adapter = binding.emoteSheetViewPager.adapter as? EmoteSheetAdapter ?: return
            runCatching {
                binding.emoteSheetViewPager.recyclerView?.forEachViewHolder<EmoteSheetAdapter.ViewHolder>(adapter.itemCount) { idx, viewHolder ->
                    viewHolder.binding.buttonsLayout.isNestedScrollingEnabled = idx == position
                }
            }
        }
    }

    private fun sendResultAndDismiss(result: EmoteSheetResult) {
        findNavController()
            .getBackStackEntry(R.id.mainFragment)
            .savedStateHandle[MainFragment.EMOTE_SHEET_RESULT_KEY] = result
        dialog?.dismiss()
    }
}
