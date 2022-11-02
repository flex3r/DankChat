package com.flxrs.dankchat.preferences.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.HighlightsIgnoresBottomsheetBinding
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.preferences.ui.highlights.HighlightEvent
import com.flxrs.dankchat.preferences.ui.highlights.HighlightsTab
import com.flxrs.dankchat.preferences.ui.highlights.HighlightsTabAdapter
import com.flxrs.dankchat.preferences.ui.highlights.HighlightsViewModel
import com.flxrs.dankchat.preferences.ui.ignores.*
import com.flxrs.dankchat.utils.extensions.expand
import com.flxrs.dankchat.utils.extensions.showShortSnackbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationsSettingsFragment : MaterialPreferenceFragmentCompat() {

    private val highlightsViewModel: HighlightsViewModel by viewModels()
    private val ignoresViewModel: IgnoresViewModel by viewModels()

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

        findPreference<Preference>(getString(R.string.preference_custom_mentions_key))?.apply {
            setOnPreferenceClickListener {
                showHighlightsSheet(view)
                true
            }
        }
        findPreference<Preference>(getString(R.string.preference_blacklist_key))?.apply {
            setOnPreferenceClickListener {
                showIgnoresSheet(view)
                true
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.notifications_settings, rootKey)
    }

    private fun showHighlightsSheet(root: View) {
        val context = root.context
        val binding = HighlightsIgnoresBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false)
        val adapter = HighlightsTabAdapter(
            onAddItem = { highlightsViewModel.addHighlight(HighlightsTab.values()[binding.tabs.selectedTabPosition]) },
            onDeleteItem = highlightsViewModel::removeHighlight,
        )
        with(binding) {
            sheet.updateLayoutParams {
                height = resources.displayMetrics.heightPixels
            }
            title.setText(R.string.highlights)
            viewPager.adapter = adapter
            TabLayoutMediator(tabs, viewPager) { tab, pos ->
                val highlightTab = HighlightsTab.values()[pos]
                tab.text = when (highlightTab) {
                    HighlightsTab.Messages -> getString(R.string.tab_messages)
                    HighlightsTab.Users    -> getString(R.string.tab_users)
                }
            }.attach()
        }
        BottomSheetDialog(context).apply {
            highlightsViewModel.fetchHighlights()
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    highlightsViewModel.highlightTabs.collect {
                        adapter.submitList(it)
                    }
                }
            }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    highlightsViewModel.events.collect { event ->
                        when (event) {
                            is HighlightEvent.ItemRemoved -> {
                                binding.root.showShortSnackbar(getString(R.string.item_removed)) {
                                    setAction(getString(R.string.undo)) { highlightsViewModel.addHighlightItem(event.item, event.position) }
                                }
                            }
                        }
                    }
                }
            }
            setOnDismissListener {
                highlightsViewModel.updateHighlights(adapter.currentList)
            }
            setContentView(binding.root)
            behavior.skipCollapsed = true
            behavior.isFitToContents = false
            behavior.expand()
            show()
        }
    }

    private fun showIgnoresSheet(root: View) {
        val context = root.context
        val binding = HighlightsIgnoresBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false)
        val adapter = IgnoresTabAdapter(
            onAddItem = { ignoresViewModel.addIgnore(IgnoresTab.values()[binding.tabs.selectedTabPosition]) },
            onDeleteItem = ignoresViewModel::removeIgnore,
        )
        with(binding) {
            sheet.updateLayoutParams {
                height = resources.displayMetrics.heightPixels
            }
            title.setText(R.string.ignores)
            viewPager.adapter = adapter
            TabLayoutMediator(tabs, viewPager) { tab, pos ->
                val ignoreTab = IgnoresTab.values()[pos]
                tab.text = when (ignoreTab) {
                    IgnoresTab.Messages -> getString(R.string.tab_messages)
                    IgnoresTab.Users    -> getString(R.string.tab_users)
                    IgnoresTab.Twitch   -> getString(R.string.tab_twitch)
                }
            }.attach()
        }
        BottomSheetDialog(context).apply {
            ignoresViewModel.fetchIgnores()
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    ignoresViewModel.ignoreTabs.collect {
                        adapter.submitList(it)
                    }
                }
            }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    ignoresViewModel.events.collect { event ->
                        when (event) {
                            is IgnoreEvent.UnblockError -> binding.root.showShortSnackbar(getString(R.string.unblocked_user_failed, event.item.username))
                            is IgnoreEvent.BlockError   -> binding.root.showShortSnackbar(getString(R.string.blocked_user_failed, event.item.username))
                            is IgnoreEvent.ItemRemoved  -> {
                                val snackBarText = when (event.item) {
                                    is TwitchBlockItem -> getString(R.string.unblocked_user, event.item.username)
                                    else               -> getString(R.string.item_removed)
                                }
                                binding.root.showShortSnackbar(snackBarText) {
                                    setAction(R.string.undo) { ignoresViewModel.addIgnoreItem(event.item, event.position) }
                                }
                            }
                        }
                    }
                }
            }
            setOnDismissListener {
                ignoresViewModel.updateIgnores(adapter.currentList)
            }
            setContentView(binding.root)
            behavior.skipCollapsed = true
            behavior.isFitToContents = false
            behavior.expand()
            show()
        }
    }
}