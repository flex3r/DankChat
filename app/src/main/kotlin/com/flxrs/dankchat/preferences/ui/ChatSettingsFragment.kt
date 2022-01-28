package com.flxrs.dankchat.preferences.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.updateLayoutParams
import androidx.preference.*
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.CommandsBottomsheetBinding
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.main.MainActivity
import com.flxrs.dankchat.preferences.command.CommandAdapter
import com.flxrs.dankchat.preferences.command.CommandItem
import com.flxrs.dankchat.utils.DateTimeUtils
import com.flxrs.dankchat.utils.extensions.showLongSnackbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.squareup.moshi.Moshi

class ChatSettingsFragment : PreferenceFragmentCompat() {

    private val jsonAdapter = Moshi.Builder().build().adapter(CommandItem.Entry::class.java)

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

        val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
        findPreference<Preference>(getString(R.string.preference_commands_key))?.apply {
            setOnPreferenceClickListener {
                showCommandsPreference(view, key, preferences)
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
        findPreference<MultiSelectListPreference>(getString(R.string.preference_visible_emotes_key))?.apply {
            setOnPreferenceChangeListener { _, _ ->
                view?.showLongSnackbar(getString(R.string.restart_required)) {
                    setAction(R.string.restart) {
                        // KKona
                        val restartIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        context.startActivity(restartIntent)
                        Runtime.getRuntime().exit(0)
                    }
                }
                true
            }
        }
    }

    private fun showCommandsPreference(root: View, key: String, sharedPreferences: SharedPreferences): Boolean {
        val context = root.context
        val sheetHeight = (resources.displayMetrics.heightPixels * 0.6f).toInt()
        val commands = runCatching {
            sharedPreferences
                .getStringSet(key, emptySet())
                .orEmpty()
                .mapNotNull { jsonAdapter.fromJson(it) }
                .plus(CommandItem.AddEntry)
        }.getOrDefault(emptyList())

        val commandAdapter = CommandAdapter(commands.toMutableList())
        val binding = CommandsBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
            commandsList.adapter = commandAdapter
            commandsSheet.updateLayoutParams {
                height = sheetHeight
            }
        }

        BottomSheetDialog(context).apply {
            setContentView(binding.root)
            setOnDismissListener {
                val stringSet = commandAdapter.commands
                    .filterIsInstance<CommandItem.Entry>()
                    .filter { it.trigger.isNotBlank() && it.command.isNotBlank() }
                    .map(jsonAdapter::toJson)
                    .toSet()

                sharedPreferences.edit { putStringSet(key, stringSet) }
            }
            behavior.peekHeight = sheetHeight
            show()
        }

        return true
    }

    companion object {
        private const val SCROLLBACK_LENGTH_STEP = 50
        fun correctScrollbackLength(seekbarValue: Int): Int = seekbarValue * SCROLLBACK_LENGTH_STEP
    }
}