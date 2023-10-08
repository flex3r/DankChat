package com.flxrs.dankchat.preferences.ui

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.SwitchPreferenceCompat
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StreamsSettingsFragment : MaterialPreferenceFragmentCompat() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return super.onCreateView(inflater, container, savedInstanceState).apply {
            updatePictureInPicturePreferenceVisibility()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updatePictureInPicturePreferenceVisibility()

        val binding = SettingsFragmentBinding.bind(view)
        (requireActivity() as MainActivity).apply {
            setSupportActionBar(binding.settingsToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.preference_streams_header)
            }
        }
    }

    private fun updatePictureInPicturePreferenceVisibility() {
        findPreference<SwitchPreferenceCompat>(getString(R.string.preference_pip_key))?.apply {
            isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) ?: true)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.streams_settings, rootKey)
    }
}
