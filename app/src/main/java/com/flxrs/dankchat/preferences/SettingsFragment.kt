package com.flxrs.dankchat.preferences

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.view.updateLayoutParams
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import androidx.recyclerview.widget.LinearLayoutManager
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.MainActivity
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.MultiEntryBottomsheetBinding
import com.flxrs.dankchat.preferences.multientry.MultiEntryAdapter
import com.flxrs.dankchat.preferences.multientry.MultiEntryItem
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.squareup.moshi.Moshi

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        findPreference<Preference>(getString(R.string.preference_about_key))
            ?.summary = getString(R.string.preference_about_summary, BuildConfig.VERSION_NAME)

        val isLoggedIn = DankChatPreferenceStore(requireContext()).isLoggedin()
        findPreference<Preference>(getString(R.string.preference_logout_key))?.apply {
            isEnabled = isLoggedIn
            setOnPreferenceClickListener {
                Intent().apply {
                    putExtra(MainActivity.LOGOUT_REQUEST_KEY, true)
                    requireActivity().let {
                        it.setResult(Activity.RESULT_OK, this)
                        it.finish()
                    }
                }
                true
            }
        }
        findPreference<SwitchPreference>(getString(R.string.preference_dark_theme_key))?.apply {
            setDarkMode(isChecked)
            setOnPreferenceClickListener {
                setDarkMode(isChecked)
                true
            }
        }
        findPreference<Preference>(getString(R.string.preference_custom_mentions_key))?.apply {
            setOnPreferenceClickListener {
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(MultiEntryItem.Entry::class.java)
                val entryStringSet = sharedPreferences.getStringSet(key, emptySet()) ?: emptySet()
                val entries = entryStringSet.mapNotNull { adapter.fromJson(it) }.sortedBy { it.entry }.plus(MultiEntryItem.AddEntry)

                val entryAdapter = MultiEntryAdapter().apply { submitList(entries) }
                val binding = MultiEntryBottomsheetBinding.inflate(LayoutInflater.from(requireContext())).apply {
                    multiEntryList.layoutManager = LinearLayoutManager(requireContext())
                    multiEntryList.adapter = entryAdapter
                    multiEntrySheet.updateLayoutParams {
                        height = (resources.displayMetrics.heightPixels * 0.6f).toInt()
                    }
                }
                BottomSheetDialog(requireContext()).apply {
                    setContentView(binding.root)
                    setOnDismissListener {
                        val stringSet = entryAdapter.currentList
                            .filterIsInstance<MultiEntryItem.Entry>()
                            .filter { it.entry.isNotBlank() }
                            .map { adapter.toJson(it) }
                            .toSet()

                        sharedPreferences.edit { putStringSet(key, stringSet) }
                    }
                    behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.6f).toInt()
                    show()
                }
                true
            }
        }
    }

    private fun setDarkMode(darkMode: Boolean) {
        (requireActivity() as SettingsActivity).delegate.localNightMode = if (darkMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else AppCompatDelegate.MODE_NIGHT_NO
    }
}