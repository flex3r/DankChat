package com.flxrs.dankchat.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.updateLayoutParams
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.MultiEntryBottomsheetBinding
import com.flxrs.dankchat.preferences.multientry.MultiEntryAdapter
import com.flxrs.dankchat.preferences.multientry.MultiEntryItem
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.squareup.moshi.Moshi
import kotlinx.android.synthetic.main.settings_fragment.view.*

class NotificationsSettingsFragment : PreferenceFragmentCompat() {

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(MultiEntryItem.Entry::class.java)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.settings_toolbar
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.preference_notifications_mentions_header)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.notifications_settings, rootKey)

        findPreference<Preference>(getString(R.string.preference_custom_mentions_key))?.apply {
            setOnPreferenceClickListener { showMultiEntryPreference(key, sharedPreferences) }
        }
        findPreference<Preference>(getString(R.string.preference_blacklist_key))?.apply {
            setOnPreferenceClickListener { showMultiEntryPreference(key, sharedPreferences) }
        }
    }

    private fun showMultiEntryPreference(
        key: String,
        sharedPreferences: SharedPreferences
    ): Boolean {
        val entryStringSet = sharedPreferences.getStringSet(key, emptySet()) ?: emptySet()
        val entries = entryStringSet.mapNotNull { adapter.fromJson(it) }.sortedBy { it.entry }
            .plus(MultiEntryItem.AddEntry)

        val entryAdapter = MultiEntryAdapter(entries.toMutableList())
        val binding =
            MultiEntryBottomsheetBinding.inflate(LayoutInflater.from(requireContext())).apply {
                multiEntryList.layoutManager = LinearLayoutManager(requireContext())
                multiEntryList.adapter = entryAdapter
                multiEntrySheet.updateLayoutParams {
                    height = (resources.displayMetrics.heightPixels * 0.6f).toInt()
                }
            }
        BottomSheetDialog(requireContext()).apply {
            setContentView(binding.root)
            setOnDismissListener {
                val stringSet = entryAdapter.entries
                    .filterIsInstance<MultiEntryItem.Entry>()
                    .filter { it.entry.isNotBlank() }
                    .map { adapter.toJson(it) }
                    .toSet()

                sharedPreferences.edit { putStringSet(key, stringSet) }
            }
            behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.6f).toInt()
            show()
        }
        return true
    }
}