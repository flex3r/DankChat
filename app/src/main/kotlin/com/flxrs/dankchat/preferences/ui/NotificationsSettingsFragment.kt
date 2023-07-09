package com.flxrs.dankchat.preferences.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.viewpager2.widget.ViewPager2
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.HighlightsIgnoresBottomsheetBinding
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.ui.highlights.HighlightEvent
import com.flxrs.dankchat.preferences.ui.highlights.HighlightsTab
import com.flxrs.dankchat.preferences.ui.highlights.HighlightsTabAdapter
import com.flxrs.dankchat.preferences.ui.highlights.HighlightsViewModel
import com.flxrs.dankchat.preferences.ui.ignores.IgnoreEvent
import com.flxrs.dankchat.preferences.ui.ignores.IgnoresTab
import com.flxrs.dankchat.preferences.ui.ignores.IgnoresTabAdapter
import com.flxrs.dankchat.preferences.ui.ignores.IgnoresViewModel
import com.flxrs.dankchat.preferences.ui.ignores.TwitchBlockItem
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.flxrs.dankchat.utils.extensions.expand
import com.flxrs.dankchat.utils.extensions.showShortSnackbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationsSettingsFragment : MaterialPreferenceFragmentCompat() {

    private val highlightsViewModel: HighlightsViewModel by viewModels()
    private val ignoresViewModel: IgnoresViewModel by viewModels()
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var bottomSheetBinding: HighlightsIgnoresBottomsheetBinding? = null

    @Inject
    lateinit var preferences: DankChatPreferenceStore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = SettingsFragmentBinding.bind(view)
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(binding.settingsToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.preference_highlights_ignores_header)
            }
        }

        val highlightsAdapter = HighlightsTabAdapter(
            onAddItem = highlightsViewModel::addHighlight,
            onDeleteItem = highlightsViewModel::removeHighlight,
            preferences = preferences,
        )
        val ignoresAdapter = IgnoresTabAdapter(
            onAddItem = ignoresViewModel::addIgnore,
            onDeleteItem = ignoresViewModel::removeIgnore,
        )

        findPreference<Preference>(getString(R.string.preference_custom_mentions_key))?.apply {
            setOnPreferenceClickListener {
                bottomSheetBinding = HighlightsIgnoresBottomsheetBinding.inflate(LayoutInflater.from(view.context), view as? ViewGroup, false)
                showHighlightsSheet(highlightsAdapter)
                true
            }
        }
        findPreference<Preference>(getString(R.string.preference_blacklist_key))?.apply {
            setOnPreferenceClickListener {
                bottomSheetBinding = HighlightsIgnoresBottomsheetBinding.inflate(LayoutInflater.from(view.context), view as? ViewGroup, false)
                showIgnoresSheet(ignoresAdapter)
                true
            }
        }

        collectFlow(highlightsViewModel.highlightTabs, highlightsAdapter::submitList)
        collectFlow(highlightsViewModel.events) { event ->
            when (event) {
                is HighlightEvent.ItemRemoved -> bottomSheetBinding?.root?.showShortSnackbar(getString(R.string.item_removed)) {
                    setAction(getString(R.string.undo)) { highlightsViewModel.addHighlightItem(event.item, event.position) }
                }
            }
        }
        collectFlow(highlightsViewModel.currentTab) { currentTab ->
            bottomSheetBinding?.subTitle?.text = when (currentTab) {
                HighlightsTab.Messages         -> getString(R.string.highlights_messages_title)
                HighlightsTab.Users            -> getString(R.string.highlights_users_title)
                HighlightsTab.BlacklistedUsers -> getString(R.string.highlights_blacklisted_users_title)
            }
        }
        collectFlow(ignoresViewModel.ignoreTabs, ignoresAdapter::submitList)
        collectFlow(ignoresViewModel.events) { event ->
            when (event) {
                is IgnoreEvent.UnblockError -> bottomSheetBinding?.root?.showShortSnackbar(getString(R.string.unblocked_user_failed, event.item.username))
                is IgnoreEvent.BlockError   -> bottomSheetBinding?.root?.showShortSnackbar(getString(R.string.blocked_user_failed, event.item.username))
                is IgnoreEvent.ItemRemoved  -> {
                    val snackBarText = when (event.item) {
                        is TwitchBlockItem -> getString(R.string.unblocked_user, event.item.username)
                        else               -> getString(R.string.item_removed)
                    }
                    bottomSheetBinding?.root?.showShortSnackbar(snackBarText) {
                        setAction(R.string.undo) { ignoresViewModel.addIgnoreItem(event.item, event.position) }
                    }
                }
            }
        }
        collectFlow(ignoresViewModel.currentTab) { currentTab ->
            bottomSheetBinding?.subTitle?.text = when (currentTab) {
                IgnoresTab.Messages -> getString(R.string.ignores_messages_title)
                IgnoresTab.Users    -> getString(R.string.ignores_users_title)
                IgnoresTab.Twitch   -> getString(R.string.ignores_twitch_title)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
        bottomSheetBinding = null
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.notifications_settings, rootKey)
    }

    private fun showHighlightsSheet(highlightsAdapter: HighlightsTabAdapter) {
        val binding = bottomSheetBinding ?: return
        binding.subTitle.setText(R.string.highlights_messages_title)
        val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                highlightsViewModel.setCurrentTab(position)
            }
        }
        with(binding) {
            sheet.updateLayoutParams {
                height = resources.displayMetrics.heightPixels
            }
            title.setText(R.string.highlights)
            viewPager.registerOnPageChangeCallback(pageChangeCallback)
            viewPager.adapter = highlightsAdapter
            TabLayoutMediator(tabs, viewPager) { tab, pos ->
                val highlightTab = HighlightsTab.entries[pos]
                tab.text = when (highlightTab) {
                    HighlightsTab.Messages         -> getString(R.string.tab_messages)
                    HighlightsTab.Users            -> getString(R.string.tab_users)
                    HighlightsTab.BlacklistedUsers -> getString(R.string.tab_blacklisted_users)
                }
            }.attach()
        }

        bottomSheetDialog = BottomSheetDialog(requireContext()).apply {
            highlightsViewModel.fetchHighlights()
            setOnDismissListener {
                highlightsViewModel.updateHighlights(highlightsAdapter.currentList)
                binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
                bottomSheetDialog = null
                bottomSheetBinding = null
            }
            setContentView(binding.root)
            behavior.skipCollapsed = true
            behavior.isFitToContents = false
            behavior.expand()
            show()
        }
    }

    private fun showIgnoresSheet(ignoresAdapter: IgnoresTabAdapter) {
        val binding = bottomSheetBinding ?: return
        binding.subTitle.setText(R.string.ignores_messages_title)
        val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                ignoresViewModel.setCurrentTab(position)
            }
        }
        with(binding) {
            sheet.updateLayoutParams {
                height = resources.displayMetrics.heightPixels
            }
            title.setText(R.string.ignores)
            viewPager.registerOnPageChangeCallback(pageChangeCallback)
            viewPager.adapter = ignoresAdapter
            TabLayoutMediator(tabs, viewPager) { tab, pos ->
                val ignoreTab = IgnoresTab.entries[pos]
                tab.text = when (ignoreTab) {
                    IgnoresTab.Messages -> getString(R.string.tab_messages)
                    IgnoresTab.Users    -> getString(R.string.tab_users)
                    IgnoresTab.Twitch   -> getString(R.string.tab_twitch)
                }
            }.attach()
        }
        bottomSheetDialog = BottomSheetDialog(requireContext()).apply {
            ignoresViewModel.fetchIgnores()
            setOnDismissListener {
                ignoresViewModel.updateIgnores(ignoresAdapter.currentList)
                binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
                bottomSheetDialog = null
                bottomSheetBinding = null
            }
            setContentView(binding.root)
            behavior.skipCollapsed = true
            behavior.isFitToContents = false
            behavior.expand()
            show()
        }
    }
}
