package com.flxrs.dankchat.preferences

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.flxrs.dankchat.MainActivity
import com.flxrs.dankchat.R
import kotlinx.android.synthetic.main.settings_fragment.view.*

class DeveloperSettingsFragment : PreferenceFragmentCompat() {

    private val requestCheckTTSData = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        when (it.resultCode) {
            TextToSpeech.Engine.CHECK_VOICE_DATA_PASS -> (requireActivity() as MainActivity).setTTSEnabled(true)
            else -> startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.settings_toolbar
        (requireActivity() as MainActivity).apply {
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.preference_developer_header)
            }

            findPreference<SwitchPreferenceCompat>(getString(R.string.preference_tts_key))?.apply {
                setOnPreferenceChangeListener { _, value ->
                    when (value as Boolean) {
                        true -> requestCheckTTSData.launch(Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA))
                        else -> setTTSEnabled(false)
                    }
                    true
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.developer_settings, rootKey)
    }
}