package com.flxrs.dankchat.preferences.ui

import android.os.Bundle
import android.text.util.Linkify
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.toSpannable
import androidx.core.text.util.LinkifyCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OverviewSettingsFragment : MaterialPreferenceFragmentCompat() {

    private val navController: NavController by lazy { findNavController() }

    @Inject
    lateinit var dankChatPreferences: DankChatPreferenceStore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = SettingsFragmentBinding.bind(view)
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(binding.settingsToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.settings)
            }
        }
        navController.currentBackStackEntry?.savedStateHandle?.apply {
            getLiveData<Boolean>(MainFragment.THEME_CHANGED_KEY).observe(viewLifecycleOwner, {
                remove<Boolean>(MainFragment.THEME_CHANGED_KEY)
                navController.previousBackStackEntry?.savedStateHandle?.set(MainFragment.THEME_CHANGED_KEY, true)
            })
        }

        val isLoggedIn = dankChatPreferences.isLoggedIn
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
            isEnabled = isLoggedIn
            setOnPreferenceClickListener {
                with(navController) {
                    previousBackStackEntry?.savedStateHandle?.set(MainFragment.LOGOUT_REQUEST_KEY, true)
                    navigateUp()
                }
                true
            }
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