package com.flxrs.dankchat.preferences

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.MainFragment
import com.flxrs.dankchat.R
import kotlinx.android.synthetic.main.settings_fragment.view.*

class OverviewSettingsFragment : PreferenceFragmentCompat() {

    private val navController: NavController by lazy { findNavController() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.settings_toolbar
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.settings)
            }
        }
        navController.currentBackStackEntry?.savedStateHandle?.apply {
            getLiveData<Boolean>(MainFragment.THEME_CHANGED_KEY).observe(viewLifecycleOwner, Observer {
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
                        .addDefaultShareMenuItem()
                        .setShowTitle(true)
                        .build().launchUrl(view.context, Uri.parse(GITHUB_URL))
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