package com.flxrs.dankchat.preferences.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.preference.Preference
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.RmHostBottomsheetBinding
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.main.MainActivity
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.showRestartRequired
import com.flxrs.dankchat.utils.extensions.withTrailingSlash
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class DeveloperSettingsFragment : MaterialPreferenceFragmentCompat() {

    private var bottomSheetDialog: BottomSheetDialog? = null

    @Inject
    lateinit var dankChatPreferenceStore: DankChatPreferenceStore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = SettingsFragmentBinding.bind(view)
        (requireActivity() as MainActivity).apply {
            setSupportActionBar(binding.settingsToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.preference_developer_header)
            }
        }

        findPreference<Preference>(getString(R.string.preference_rm_host_key))?.apply {
            setOnPreferenceClickListener { showRmHostPreference(view) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.developer_settings, rootKey)
    }

    private fun showRmHostPreference(root: View): Boolean {
        val context = root.context
        val windowHeight = resources.displayMetrics.heightPixels
        val peekHeight = (windowHeight * 0.6).roundToInt()
        val currentHost = dankChatPreferenceStore.customRmHost
        val binding = RmHostBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
            rmHostInput.setText(currentHost)
            hostReset.setOnClickListener {
                rmHostInput.setText(dankChatPreferenceStore.resetRmHost())
            }
            rmHostSheet.updateLayoutParams {
                height = windowHeight
            }
        }

        bottomSheetDialog = BottomSheetDialog(context).apply {
            setContentView(binding.root)
            setOnDismissListener {
                val newHost = binding.rmHostInput.text
                    ?.toString()
                    ?.withTrailingSlash ?: return@setOnDismissListener

                if (newHost != currentHost) {
                    dankChatPreferenceStore.customRmHost = newHost
                    view?.showRestartRequired()
                }
            }
            behavior.isFitToContents = false
            behavior.peekHeight = peekHeight
            show()
        }

        return true
    }
}