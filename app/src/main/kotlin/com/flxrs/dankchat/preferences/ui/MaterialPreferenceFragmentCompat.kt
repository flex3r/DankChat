package com.flxrs.dankchat.preferences.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

abstract class MaterialPreferenceFragmentCompat : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemInsets.bottom, top = systemInsets.top)
            ViewCompat.onApplyWindowInsets(v, insets)
        }
    }
    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is ListPreference            -> showListPreferenceDialog(preference)
            is MultiSelectListPreference -> showMultiSelectListPreferenceDialog(preference)
            else                         -> super.onDisplayPreferenceDialog(preference)
        }

    }
}
