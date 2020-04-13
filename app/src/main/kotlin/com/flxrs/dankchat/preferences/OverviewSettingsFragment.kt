package com.flxrs.dankchat.preferences

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.MainFragment
import com.flxrs.dankchat.R
import kotlinx.android.synthetic.main.settings_fragment.view.*

class OverviewSettingsFragment : PreferenceFragmentCompat() {

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
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.overview_settings, rootKey)

        findPreference<Preference>(getString(R.string.preference_about_key))?.summary = getString(R.string.preference_about_summary, BuildConfig.VERSION_NAME)

        val isLoggedIn = DankChatPreferenceStore(requireContext()).isLoggedin()
        findPreference<Preference>(getString(R.string.preference_logout_key))?.apply {
            isEnabled = isLoggedIn
            setOnPreferenceClickListener {
                with(findNavController()) {
                    previousBackStackEntry?.savedStateHandle?.set(MainFragment.LOGOUT_REQUEST_KEY, true)
                    navigateUp()
                }
                true
            }
        }

    }
}