package com.flxrs.dankchat.preferences.screens

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.util.LinkifyCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.databinding.UploaderBottomsheetBinding
import com.flxrs.dankchat.main.MainActivity
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ToolsSettingsFragment : PreferenceFragmentCompat() {
    @Inject
    lateinit var dankChatPreferenceStore: DankChatPreferenceStore

    private val requestCheckTTSData = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        when (it.resultCode) {
            TextToSpeech.Engine.CHECK_VOICE_DATA_PASS -> (activity as? MainActivity)?.setTTSEnabled(true)
            else                                      -> startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = SettingsFragmentBinding.bind(view)
        (requireActivity() as MainActivity).apply {
            setSupportActionBar(binding.settingsToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.preference_tools_header)
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

        findPreference<Preference>(getString(R.string.preference_uploader_key))?.apply {
            setOnPreferenceClickListener { showImageUploaderPreference(view) }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.tools_settings, rootKey)
    }

    private fun showImageUploaderPreference(root: View): Boolean {
        val context = root.context

        val binding = UploaderBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
            uploader = dankChatPreferenceStore.customImageUploader
            uploaderReset.setOnClickListener {
                uploader = dankChatPreferenceStore.resetImageUploader()
            }
            uploaderSheet.updateLayoutParams {
                height = (resources.displayMetrics.heightPixels * 0.6f).toInt()
            }
            LinkifyCompat.addLinks(uploaderDescription, Linkify.WEB_URLS)
        }

        BottomSheetDialog(context).apply {
            setContentView(binding.root)
            setOnDismissListener {
                val uploader = binding.uploader ?: DankChatPreferenceStore.DEFAULT_UPLOADER
                dankChatPreferenceStore.customImageUploader = uploader.copy(
                    headers = uploader.headers?.takeIf { it.isNotBlank() },
                    imageLinkPattern = uploader.imageLinkPattern?.takeIf { it.isNotBlank() },
                    deletionLinkPattern = uploader.deletionLinkPattern?.takeIf { it.isNotBlank() },
                )
            }
            behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.6f).toInt()
            show()
        }

        return true
    }
}