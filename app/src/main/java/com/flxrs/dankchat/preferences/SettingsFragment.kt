package com.flxrs.dankchat.preferences

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.MainActivity
import com.flxrs.dankchat.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        findPreference<Preference>(getString(R.string.preference_about_key))
            ?.summary = getString(R.string.preference_about_summary, BuildConfig.VERSION_NAME)

        val isLoggedIn = DankChatPreferenceStore(requireContext()).isLoggedin()
        findPreference<Preference>(getString(R.string.preference_logout_key))?.apply {
            isEnabled = isLoggedIn
            setOnPreferenceClickListener {
                Intent().apply {
                    putExtra(MainActivity.LOGOUT_REQUEST_KEY, true)
                    requireActivity().let {
                        it.setResult(Activity.RESULT_OK, this)
                        it.finish()
                    }
                }
                true
            }
        }
        findPreference<SwitchPreference>(getString(R.string.preference_dark_theme_key))?.apply {
            setDarkMode(isChecked)
            setOnPreferenceClickListener {
                setDarkMode(isChecked)
                true
            }
        }
    }

    private fun setDarkMode(darkMode: Boolean) {
        (requireActivity() as SettingsActivity).delegate.localNightMode = if (darkMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else AppCompatDelegate.MODE_NIGHT_NO
    }
}