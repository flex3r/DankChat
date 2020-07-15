package com.flxrs.dankchat.preferences

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.flxrs.dankchat.R
import com.flxrs.dankchat.utils.TimeUtils
import kotlinx.android.synthetic.main.settings_fragment.view.*

class ChatSettingsFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.settings_toolbar
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.preference_chat_header)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.chat_settings, rootKey)

        findPreference<ListPreference>(getString(R.string.preference_timestamp_format_key))?.setOnPreferenceChangeListener { _, newValue ->
            (newValue as? String)?.let { TimeUtils.setPattern(it) }
            true
        }
    }
}