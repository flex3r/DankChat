package com.flxrs.dankchat.preferences.ui

import android.os.Bundle
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.toSpannable
import androidx.core.text.util.LinkifyCompat
import androidx.core.view.doOnPreDraw
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.R
import com.flxrs.dankchat.changelog.DankChatVersion
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.navigateSafe
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OverviewSettingsFragment : MaterialPreferenceFragmentCompat() {

    private val navController: NavController by lazy { findNavController() }

    @Inject
    lateinit var dankChatPreferences: DankChatPreferenceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitTransition = MaterialFadeThrough()
        reenterTransition = MaterialFadeThrough()
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return super.onCreateView(inflater, container, savedInstanceState).apply {
            findPreference<Preference>(getString(R.string.preference_about_key))?.apply {
                val spannable = buildString {
                    append(context.getString(R.string.preference_about_summary, BuildConfig.VERSION_NAME))
                    append("\n")
                    append(GITHUB_URL)
                    append("\n\n")
                    append(context.getString(R.string.preference_about_tos))
                    append("\n")
                    append(TWITCH_TOS_URL)
                }.toSpannable()
                LinkifyCompat.addLinks(spannable, Linkify.WEB_URLS)
                summary = spannable
            }

            findPreference<Preference>(getString(R.string.preference_logout_key))?.apply {
                isEnabled = dankChatPreferences.isLoggedIn
                setOnPreferenceClickListener {
                    with(navController) {
                        previousBackStackEntry?.savedStateHandle?.set(MainFragment.LOGOUT_REQUEST_KEY, true)
                        navigateUp()
                    }
                    true
                }
            }

            findPreference<Preference>(getString(R.string.preference_whats_new_key))?.apply {
                isVisible = DankChatVersion.HAS_CHANGELOG
                setOnPreferenceClickListener {
                    navigateSafe(R.id.action_overviewSettingsFragment_to_changelogSheetFragment)
                    true
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        (view.parent as? View)?.doOnPreDraw { startPostponedEnterTransition() }

        SettingsFragmentBinding.bind(view).apply {
            settingsToolbar.setNavigationOnClickListener { navController.navigateUp() }
            settingsToolbar.title = getString(R.string.settings)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.overview_settings, rootKey)
    }

    companion object {
        private const val GITHUB_URL = "https://github.com/flex3r/dankchat"
        private const val TWITCH_TOS_URL = "https://www.twitch.tv/p/terms-of-service"
    }
}
