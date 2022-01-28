package com.flxrs.dankchat.preferences.ui

import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

abstract class MaterialPreferenceFragmentCompat : PreferenceFragmentCompat() {
    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is ListPreference            -> showListPreferenceDialog(preference)
            is MultiSelectListPreference -> showMultiSelectListPreferenceDialog(preference)
            else                         -> super.onDisplayPreferenceDialog(preference)
        }

    }
}