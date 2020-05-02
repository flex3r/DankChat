package com.flxrs.dankchat.preferences

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import com.flxrs.dankchat.MainFragment
import com.flxrs.dankchat.R
import kotlinx.android.synthetic.main.settings_fragment.view.*

class AppearanceSettingsFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.settings_toolbar
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.preference_appearance_header)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.appearance_settings, rootKey)

        findPreference<SwitchPreference>(getString(R.string.preference_dark_theme_key))?.apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
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