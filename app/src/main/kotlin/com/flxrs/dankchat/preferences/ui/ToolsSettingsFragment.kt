package com.flxrs.dankchat.preferences.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.text.util.LinkifyCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.RecentUploadsBottomsheetBinding
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.databinding.TtsIgnoreListBottomsheetBinding
import com.flxrs.dankchat.databinding.UploaderBottomsheetBinding
import com.flxrs.dankchat.main.MainActivity
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.tts.TtsIgnoreItem
import com.flxrs.dankchat.preferences.tts.TtsIgnoreListAdapter
import com.flxrs.dankchat.preferences.upload.RecentUploadsAdapter
import com.flxrs.dankchat.preferences.upload.RecentUploadsViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class ToolsSettingsFragment : MaterialPreferenceFragmentCompat() {
    private val requestCheckTTSData = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
            startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        }
    }

    private val viewModel: RecentUploadsViewModel by viewModels()

    @Inject
    lateinit var dankChatPreferenceStore: DankChatPreferenceStore


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
                    if (value as Boolean) {
                        requestCheckTTSData.launch(Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA))
                    }

                    true
                }
            }
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
        findPreference<Preference>(getString(R.string.preference_uploader_key))?.apply {
            setOnPreferenceClickListener { showImageUploaderPreference(view) }
        }

        findPreference<Preference>(getString(R.string.preference_tts_user_ignore_list_key))?.apply {
            setOnPreferenceClickListener { showTtsIgnoreListPreference(view, key, preferences) }
        }

        findPreference<Preference>(getString(R.string.preference_uploader_recent_uploads_key))?.apply {
            setOnPreferenceClickListener { showRecentUploads(view) }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.tools_settings, rootKey)
    }

    private fun showImageUploaderPreference(root: View): Boolean {
        val context = root.context
        val windowHeight = resources.displayMetrics.heightPixels
        val peekHeight = (windowHeight * 0.6).roundToInt()
        val binding = UploaderBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
            uploader = dankChatPreferenceStore.customImageUploader
            uploaderReset.setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.reset_media_uploader_dialog_title)
                    .setMessage(R.string.reset_media_uploader_dialog_message)
                    .setPositiveButton(R.string.reset_media_uploader_dialog_positive) { _, _ -> uploader = dankChatPreferenceStore.resetImageUploader() }
                    .setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                    .create().show()
            }
            uploaderSheet.updateLayoutParams {
                height = windowHeight
            }
            LinkifyCompat.addLinks(uploaderDescription, Linkify.WEB_URLS)
        }

        BottomSheetDialog(context).apply {
            setContentView(binding.root)
            setOnDismissListener {
                binding.uploader?.let { uploader ->
                    val validated = uploader.copy(
                        headers = uploader.headers?.takeIf { it.isNotBlank() },
                        imageLinkPattern = uploader.imageLinkPattern?.takeIf { it.isNotBlank() },
                        deletionLinkPattern = uploader.deletionLinkPattern?.takeIf { it.isNotBlank() },
                    )
                    dankChatPreferenceStore.customImageUploader = validated
                } ?: dankChatPreferenceStore.resetImageUploader()
            }
            behavior.isFitToContents = false
            behavior.peekHeight = peekHeight
            show()
        }

        return true
    }

    private fun showTtsIgnoreListPreference(root: View, key: String, sharedPreferences: SharedPreferences): Boolean {
        val context = root.context
        val windowHeight = resources.displayMetrics.heightPixels
        val peekHeight = (windowHeight * 0.6).roundToInt()
        val items = runCatching {
            sharedPreferences
                .getStringSet(key, emptySet())
                .orEmpty()
                .map { TtsIgnoreItem.Entry(it) }
                .plus(TtsIgnoreItem.AddEntry)
        }.getOrDefault(emptyList())

        val ttsIgnoreListAdapter = TtsIgnoreListAdapter(items.toMutableList())
        val binding = TtsIgnoreListBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
            ttsIgnoreList.adapter = ttsIgnoreListAdapter
            ttsIgnoreListSheet.updateLayoutParams {
                height = windowHeight
            }
        }

        BottomSheetDialog(context).apply {
            setContentView(binding.root)
            setOnDismissListener {
                val stringSet = ttsIgnoreListAdapter.items
                    .filterIsInstance<TtsIgnoreItem.Entry>()
                    .filter { it.user.isNotBlank() }
                    .map { it.user }
                    .toSet()

                sharedPreferences.edit { putStringSet(key, stringSet) }
            }
            behavior.isFitToContents = false
            behavior.peekHeight = peekHeight
            show()
        }

        return true
    }

    private fun showRecentUploads(root: View): Boolean {
        val context = root.context
        val windowHeight = resources.displayMetrics.heightPixels
        val peekHeight = (windowHeight * 0.6).roundToInt()

        val adapter = RecentUploadsAdapter()
        val binding = RecentUploadsBottomsheetBinding.inflate(LayoutInflater.from(context), view as? ViewGroup, false).apply {
            uploadsList.adapter = adapter
            uploadsSheet.updateLayoutParams {
                height = windowHeight
            }
            clearUploads.setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.clear_recent_uploads_dialog_title)
                    .setMessage(R.string.clear_recent_uploads_dialog_message)
                    .setPositiveButton(R.string.clear_recent_uploads_dialog_positive) { _, _ -> viewModel.clearUploads() }
                    .setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                    .create().show()
            }
        }

        val collectJob = lifecycleScope.launchWhenStarted {
            viewModel.getRecentUploads()
                .flowWithLifecycle(lifecycle)
                .collect {
                    binding.clearUploads.isEnabled = it.isNotEmpty()
                    adapter.submitList(it)
                }
        }

        BottomSheetDialog(context).apply {
            setContentView(binding.root)
            setOnDismissListener { collectJob.cancel() }
            behavior.isFitToContents = false
            behavior.peekHeight = peekHeight
            show()
        }

        return true
    }
}