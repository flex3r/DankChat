package com.flxrs.dankchat.preferences

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat.getSystemService
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.utils.extensions.isSystemLightMode

class AppearanceSettingsFragment : PreferenceFragmentCompat() {

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

        val followSystemModePreference = findPreference<SwitchPreferenceCompat>(getString(R.string.preference_follow_system_key)) ?: return
        val darkModePreference = findPreference<SwitchPreferenceCompat>(getString(R.string.preference_dark_theme_key)) ?: return
        val trueDarkModePreference = findPreference<SwitchPreferenceCompat>(getString(R.string.preference_true_dark_theme_key)) ?: return
        val lightModePreference = findPreference<SwitchPreferenceCompat>(getString(R.string.preference_light_theme_key)) ?: return

        fun updateThemeSwitches(followSystem: Boolean = false, darkMode: Boolean = false, lightMode: Boolean = false) {
            followSystemModePreference.isChecked = followSystem
            darkModePreference.isChecked = darkMode
            lightModePreference.isChecked = lightMode

            if (lightMode || isSystemLightMode) {
                trueDarkModePreference.isChecked = false
                trueDarkModePreference.isEnabled = false
            }
        }


        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 && !isTV) {
            // Force dark mode below 8.1
            followSystemModePreference.isEnabled = false
            lightModePreference.isEnabled = false
            updateThemeSwitches(darkMode = true)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // System dark mode was introduced in Android 10
            followSystemModePreference.isEnabled = false

            // Default value is true, set manual dark mode instead
            if (followSystemModePreference.isChecked) {
                updateThemeSwitches(darkMode = true)
            }
        }

        // Disable true dark mode switch when light mode is active
        if (lightModePreference.isChecked || isSystemLightMode) {
            trueDarkModePreference.isChecked = false
            trueDarkModePreference.isEnabled = false
        }

        followSystemModePreference.setOnPreferenceChangeListener { _,_ ->
            if (followSystemModePreference.isChecked) {
                return@setOnPreferenceChangeListener false
            }

            setDarkMode(followSystem = true)
            updateThemeSwitches(followSystem = true)
            true
        }
        darkModePreference.setOnPreferenceChangeListener { _, _ ->
            if (darkModePreference.isChecked) {
                return@setOnPreferenceChangeListener false
            }

            setDarkMode()
            updateThemeSwitches(darkMode = true)
            true
        }
        lightModePreference.setOnPreferenceChangeListener { _,_ ->
            if (lightModePreference.isChecked) {
                return@setOnPreferenceChangeListener false
            }

            setDarkMode(darkMode = false)
            updateThemeSwitches(lightMode = true)
            true
        }

        findPreference<SeekBarPreference>(getString(R.string.preference_font_size_key))?.apply {
            summary = getFontSizeSummary(value)
            setOnPreferenceChangeListener { _, _ ->
                summary = getFontSizeSummary(value)
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
        findNavController().previousBackStackEntry?.savedStateHandle?.set(MainFragment.THEME_CHANGED_KEY, true)
        AppCompatDelegate.setDefaultNightMode(
            when {
                followSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                darkMode     -> AppCompatDelegate.MODE_NIGHT_YES
                else         -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }
}