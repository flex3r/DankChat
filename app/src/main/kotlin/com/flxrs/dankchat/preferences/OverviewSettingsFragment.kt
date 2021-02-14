package com.flxrs.dankchat.preferences

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.MainFragment
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.SettingsFragmentBinding

class OverviewSettingsFragment : PreferenceFragmentCompat() {

    private val navController: NavController by lazy { findNavController() }

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

        val isLoggedIn = DankChatPreferenceStore(view.context).isLoggedIn
        findPreference<Preference>(getString(R.string.preference_about_key))?.apply {
            summary = getString(R.string.preference_about_summary, BuildConfig.VERSION_NAME)
            setOnPreferenceClickListener {
                try {
                    CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .build().launchUrl(view.context, GITHUB_URL.toUri())
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                }
                true
            }
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
        private val TAG = OverviewSettingsFragment::class.java.simpleName
        private const val GITHUB_URL = "https://github.com/flex3r/dankchat"
    }
}