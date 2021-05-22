package com.flxrs.dankchat.preferences

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.utils.DateTimeUtils

class ChatSettingsFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = SettingsFragmentBinding.bind(view)
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(binding.settingsToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.preference_chat_header)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.chat_settings, rootKey)

        findPreference<ListPreference>(getString(R.string.preference_timestamp_format_key))?.setOnPreferenceChangeListener { _, newValue ->
            (newValue as? String)?.let { DateTimeUtils.setPattern(it) }
            true
        }
        findPreference<SeekBarPreference>(getString(R.string.preference_scrollback_length_key))?.apply {
            summary = correctScrollbackLength(value).toString()
            setOnPreferenceChangeListener { _, newValue ->
                (newValue as? Int)?.let { summary = correctScrollbackLength(it).toString() }
                true
            }
        }
    }

    companion object {
        private const val SCROLLBACK_LENGTH_STEP = 50
        fun correctScrollbackLength(seekbarValue: Int): Int = seekbarValue * SCROLLBACK_LENGTH_STEP
    }
}