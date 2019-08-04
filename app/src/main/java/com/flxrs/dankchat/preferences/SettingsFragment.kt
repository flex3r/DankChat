package com.flxrs.dankchat.preferences

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.flxrs.dankchat.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }
}