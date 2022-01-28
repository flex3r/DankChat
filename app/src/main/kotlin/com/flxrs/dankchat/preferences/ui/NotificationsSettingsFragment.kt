package com.flxrs.dankchat.preferences.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.updateLayoutParams
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.MultiEntryBottomsheetBinding
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.preferences.multientry.MultiEntryAdapter
import com.flxrs.dankchat.preferences.multientry.MultiEntryItem
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.squareup.moshi.Moshi

class NotificationsSettingsFragment : MaterialPreferenceFragmentCompat() {

    private val jsonAdapter = Moshi.Builder().build().adapter(MultiEntryItem.Entry::class.java)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = SettingsFragmentBinding.bind(view)
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(binding.settingsToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.preference_notifications_mentions_header)
            }
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
        findPreference<Preference>(getString(R.string.preference_custom_mentions_key))?.apply {
            setOnPreferenceClickListener { showMultiEntryPreference(view, key, preferences, title) }
        }
        findPreference<Preference>(getString(R.string.preference_blacklist_key))?.apply {
            setOnPreferenceClickListener { showMultiEntryPreference(view, key, preferences, title) }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.notifications_settings, rootKey)
    }

    private fun showMultiEntryPreference(root: View, key: String, sharedPreferences: SharedPreferences, title: CharSequence?): Boolean {
        val context = root.context
        val sheetHeight = (resources.displayMetrics.heightPixels * 0.6f).toInt()
        val entries = runCatching {
            sharedPreferences
                .getStringSet(key, emptySet())
                .orEmpty()
                .mapNotNull { jsonAdapter.fromJson(it) }
                .sortedBy { it.entry }
                .plus(MultiEntryItem.AddEntry)
        }.getOrDefault(emptyList())

        val entryAdapter = MultiEntryAdapter(entries.toMutableList())
        val binding = MultiEntryBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
            multiEntryTitle.text = title ?: ""
            multiEntryList.adapter = entryAdapter
            multiEntrySheet.updateLayoutParams {
                height = sheetHeight
            }
        }

        BottomSheetDialog(context).apply {
            setContentView(binding.root)
            setOnDismissListener {
                val stringSet = entryAdapter.entries
                    .filterIsInstance<MultiEntryItem.Entry>()
                    .filter { it.entry.isNotBlank() }
                    .map(jsonAdapter::toJson)
                    .toSet()

                sharedPreferences.edit { putStringSet(key, stringSet) }
            }
            behavior.peekHeight = sheetHeight
            show()
        }

        return true
    }
}