package com.flxrs.dankchat.preferences.ui

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.preference.ListPreference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.utils.extensions.isSystemLightMode

class AppearanceSettingsFragment : MaterialPreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = SettingsFragmentBinding.bind(view)
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(binding.settingsToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.preference_appearance_header)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.appearance_settings, rootKey)
        val isTV = context?.let {
            val uiModeManager = getSystemService(it, UiModeManager::class.java)
            uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        } ?: false

        val themeModePreference = findPreference<ListPreference>(getString(R.string.preference_theme_key)) ?: return
        val trueDarkModePreference = findPreference<SwitchPreferenceCompat>(getString(R.string.preference_true_dark_theme_key)) ?: return
        val darkModeKey = getString(R.string.preference_dark_theme_key)
        val lightModeKey = getString(R.string.preference_light_theme_key)
        val followSystemKey = getString(R.string.preference_follow_system_key)

        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 && !isTV -> {
                // Force dark mode below 8.1
                themeModePreference.isEnabled = false
                themeModePreference.value = darkModeKey
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q              -> {
                themeModePreference.entries = arrayOf(
                    getString(R.string.preference_dark_theme_entry_title),
                    getString(R.string.preference_light_theme_entry_title)
                )
                themeModePreference.entryValues = arrayOf(
                    darkModeKey,
                    lightModeKey
                )

                // System dark mode was introduced in Android 10
                if (themeModePreference.value == followSystemKey) {
                    themeModePreference.value = darkModeKey
                }
            }
        }

        // Disable true dark mode switch when light mode is active
        if (themeModePreference.value == lightModeKey || isSystemLightMode) {
            trueDarkModePreference.isChecked = false
            trueDarkModePreference.isEnabled = false
        }

        themeModePreference.setSummaryProvider {
            when (themeModePreference.value) {
                getString(R.string.preference_follow_system_key) -> getString(R.string.preference_follow_system_title)
                getString(R.string.preference_dark_theme_key)    -> getString(R.string.preference_dark_theme_entry_title)
                getString(R.string.preference_light_theme_key)   -> getString(R.string.preference_light_theme_entry_title)
                else                                             -> ""
            }
        }
        themeModePreference.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == lightModeKey || isSystemLightMode) {
                trueDarkModePreference.isChecked = false
                trueDarkModePreference.isEnabled = false
            }

            setDarkMode(
                darkMode = newValue == darkModeKey,
                followSystem = newValue == followSystemKey
            )
            true
        }

        trueDarkModePreference.setOnPreferenceChangeListener { _, _ ->
            val activity = activity ?: return@setOnPreferenceChangeListener false

            view?.post { ActivityCompat.recreate(activity) }
            true
        }

        findPreference<SeekBarPreference>(getString(R.string.preference_font_size_key))?.apply {
            summary = getFontSizeSummary(value)
            setOnPreferenceChangeListener { _, newValue ->
                summary = getFontSizeSummary(newValue as Int)
                true
            }
        }
    }

    private fun getFontSizeSummary(value: Int): String {
        return when {
            value < 13      -> getString(R.string.preference_font_size_summary_very_small)
            value in 13..17 -> getString(R.string.preference_font_size_summary_small)
            value in 18..22 -> getString(R.string.preference_font_size_summary_large)
            else            -> getString(R.string.preference_font_size_summary_very_large)
        }
    }

    private fun setDarkMode(darkMode: Boolean = true, followSystem: Boolean = false) {
        AppCompatDelegate.setDefaultNightMode(
            when {
                followSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                darkMode     -> AppCompatDelegate.MODE_NIGHT_YES
                else         -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
        val activity = activity ?: return
        ActivityCompat.recreate(activity)
    }
}