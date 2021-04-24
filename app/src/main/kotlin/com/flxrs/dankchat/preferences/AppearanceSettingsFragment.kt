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
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.SettingsFragmentBinding

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

        findPreference<SwitchPreferenceCompat>(getString(R.string.preference_dark_theme_key))?.apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 && !isTV) {
                if (!isChecked) {
                    isChecked = true
                }
                isVisible = false
            }
            setOnPreferenceClickListener {
                setDarkMode(isChecked)
                true
            }
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
            value < 13 -> getString(R.string.preference_font_size_summary_very_small)
            value in 13..17 -> getString(R.string.preference_font_size_summary_small)
            value in 18..22 -> getString(R.string.preference_font_size_summary_large)
            else -> getString(R.string.preference_font_size_summary_very_large)
        }
    }

    private fun setDarkMode(darkMode: Boolean) {
        findNavController().previousBackStackEntry?.savedStateHandle?.set(MainFragment.THEME_CHANGED_KEY, true)
        AppCompatDelegate.setDefaultNightMode(
            when {
                darkMode -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }
}