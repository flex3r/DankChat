package com.flxrs.dankchat.preferences.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.fragment.findNavController
import com.flxrs.dankchat.theme.DankChatTheme
import com.google.android.material.transition.MaterialFadeThrough

class NotificationsSettingsFragment : Fragment() {

//    private val highlightsViewModel: HighlightsViewModel by viewModel()
//    private val ignoresViewModel: IgnoresViewModel by viewModel()
//    private var bottomSheetDialog: BottomSheetDialog? = null
//    private var bottomSheetBinding: HighlightsIgnoresBottomsheetBinding? = null
//
//    private val preferences: DankChatPreferenceStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        returnTransition = MaterialFadeThrough()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        (view.parent as? View)?.doOnPreDraw { startPostponedEnterTransition() }

//        SettingsFragmentBinding.bind(view).apply {
//            settingsToolbar.setNavigationOnClickListener { findNavController().navigateUp() }
//            settingsToolbar.title = getString(R.string.preference_highlights_ignores_header)
//        }
//
//        val highlightsAdapter = HighlightsTabAdapter(
//            onAddItem = highlightsViewModel::addHighlight,
//            onDeleteItem = highlightsViewModel::removeHighlight,
//            preferences = preferences,
//        )
//        val ignoresAdapter = IgnoresTabAdapter(
//            onAddItem = ignoresViewModel::addIgnore,
//            onDeleteItem = ignoresViewModel::removeIgnore,
//        )
//
//        findPreference<Preference>(getString(R.string.preference_custom_mentions_key))?.apply {
//            setOnPreferenceClickListener {
//                bottomSheetBinding = HighlightsIgnoresBottomsheetBinding.inflate(LayoutInflater.from(view.context), view as? ViewGroup, false)
//                showHighlightsSheet(highlightsAdapter)
//                true
//            }
//        }
//        findPreference<Preference>(getString(R.string.preference_blacklist_key))?.apply {
//            setOnPreferenceClickListener {
//                bottomSheetBinding = HighlightsIgnoresBottomsheetBinding.inflate(LayoutInflater.from(view.context), view as? ViewGroup, false)
//                showIgnoresSheet(ignoresAdapter)
//                true
//            }
//        }
//
//        collectFlow(highlightsViewModel.highlightTabs, highlightsAdapter::submitList)
//        collectFlow(highlightsViewModel.events) { event ->
//            when (event) {
//                is HighlightEvent.ItemRemoved -> bottomSheetBinding?.root?.showShortSnackbar(getString(
//                    R.string.item_removed)) {
//                    setAction(getString(R.string.undo)) { highlightsViewModel.addHighlightItem(event.item, event.position) }
//                }
//            }
//        }
//        collectFlow(highlightsViewModel.currentTab) { currentTab ->
//            bottomSheetBinding?.subTitle?.text = when (currentTab) {
//                HighlightsTab.Messages         -> getString(R.string.highlights_messages_title)
//                HighlightsTab.Users            -> getString(R.string.highlights_users_title)
//                HighlightsTab.BlacklistedUsers -> getString(R.string.highlights_blacklisted_users_title)
//            }
//        }
//        collectFlow(ignoresViewModel.ignoreTabs, ignoresAdapter::submitList)
//        collectFlow(ignoresViewModel.events) { event ->
//            when (event) {
//                is IgnoreEvent.UnblockError -> bottomSheetBinding?.root?.showShortSnackbar(getString(
//                    R.string.unblocked_user_failed, event.item.username))
//                is IgnoreEvent.BlockError   -> bottomSheetBinding?.root?.showShortSnackbar(getString(
//                    R.string.blocked_user_failed, event.item.username))
//                is IgnoreEvent.ItemRemoved  -> {
//                    val snackBarText = when (event.item) {
//                        is TwitchBlockItem -> getString(R.string.unblocked_user, event.item.username)
//                        else               -> getString(R.string.item_removed)
//                    }
//                    bottomSheetBinding?.root?.showShortSnackbar(snackBarText) {
//                        setAction(R.string.undo) { ignoresViewModel.addIgnoreItem(event.item, event.position) }
//                    }
//                }
//            }
//        }
//        collectFlow(ignoresViewModel.currentTab) { currentTab ->
//            bottomSheetBinding?.subTitle?.text = when (currentTab) {
//                IgnoresTab.Messages -> getString(R.string.ignores_messages_title)
//                IgnoresTab.Users    -> getString(R.string.ignores_users_title)
//                IgnoresTab.Twitch   -> getString(R.string.ignores_twitch_title)
//            }
//        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DankChatTheme {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = NotificationsSettingsRoute.NotificationsSettings,
                        enterTransition = { fadeIn() + scaleIn(initialScale = 0.92f) },
                        exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() + scaleIn(initialScale = 0.92f) },
                        popExitTransition = { fadeOut() },
                    ) {
                        composable<NotificationsSettingsRoute.NotificationsSettings> {
                            NotificationsSettingsScreen(
                                onNavToHighlights = { navController.navigate(NotificationsSettingsRoute.Highlights) },
                                onNavToIgnores = { navController.navigate(NotificationsSettingsRoute.Ignores) },
                                onNavBack = { findNavController().popBackStack() },
                            )
                        }
                        composable<NotificationsSettingsRoute.Highlights> {
                            /*HighlightsScreen(
                                onNavBack = { navController.popBackStack() },
                            )*/
                        }
                        composable<NotificationsSettingsRoute.Ignores> {
                            /*IgnoresScreen(
                                onNavBack = { navController.popBackStack() },
                            )*/
                        }
                    }
                }
            }
        }
    }

//    private fun showHighlightsSheet(highlightsAdapter: HighlightsTabAdapter) {
//        val binding = bottomSheetBinding ?: return
//        binding.subTitle.setText(R.string.highlights_messages_title)
//        val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
//            override fun onPageSelected(position: Int) {
//                highlightsViewModel.setCurrentTab(position)
//            }
//        }
//        with(binding) {
//            sheet.updateLayoutParams {
//                height = resources.displayMetrics.heightPixels
//            }
//            title.setText(R.string.highlights)
//            viewPager.registerOnPageChangeCallback(pageChangeCallback)
//            viewPager.adapter = highlightsAdapter
//            TabLayoutMediator(tabs, viewPager) { tab, pos ->
//                val highlightTab = HighlightsTab.entries[pos]
//                tab.text = when (highlightTab) {
//                    HighlightsTab.Messages -> getString(R.string.tab_messages)
//                    HighlightsTab.Users -> getString(R.string.tab_users)
//                    HighlightsTab.BlacklistedUsers -> getString(R.string.tab_blacklisted_users)
//                }
//            }.attach()
//        }
//
//        bottomSheetDialog = BottomSheetDialog(requireContext()).apply {
//            highlightsViewModel.fetchHighlights()
//            setOnDismissListener {
//                highlightsViewModel.updateHighlights(highlightsAdapter.currentList)
//                binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
//                bottomSheetDialog = null
//                bottomSheetBinding = null
//            }
//            setContentView(binding.root)
//            behavior.skipCollapsed = true
//            behavior.isFitToContents = false
//            behavior.expand()
//            show()
//        }
//    }
//
//    private fun showIgnoresSheet(ignoresAdapter: IgnoresTabAdapter) {
//        val binding = bottomSheetBinding ?: return
//        binding.subTitle.setText(R.string.ignores_messages_title)
//        val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
//            override fun onPageSelected(position: Int) {
//                ignoresViewModel.setCurrentTab(position)
//            }
//        }
//        with(binding) {
//            sheet.updateLayoutParams {
//                height = resources.displayMetrics.heightPixels
//            }
//            title.setText(R.string.ignores)
//            viewPager.registerOnPageChangeCallback(pageChangeCallback)
//            viewPager.adapter = ignoresAdapter
//            TabLayoutMediator(tabs, viewPager) { tab, pos ->
//                val ignoreTab = IgnoresTab.entries[pos]
//                tab.text = when (ignoreTab) {
//                    IgnoresTab.Messages -> getString(R.string.tab_messages)
//                    IgnoresTab.Users -> getString(R.string.tab_users)
//                    IgnoresTab.Twitch -> getString(R.string.tab_twitch)
//                }
//            }.attach()
//        }
//        bottomSheetDialog = BottomSheetDialog(requireContext()).apply {
//            ignoresViewModel.fetchIgnores()
//            setOnDismissListener {
//                ignoresViewModel.updateIgnores(ignoresAdapter.currentList)
//                binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
//                bottomSheetDialog = null
//                bottomSheetBinding = null
//            }
//            setContentView(binding.root)
//            behavior.skipCollapsed = true
//            behavior.isFitToContents = false
//            behavior.expand()
//            show()
//        }
//    }
}
