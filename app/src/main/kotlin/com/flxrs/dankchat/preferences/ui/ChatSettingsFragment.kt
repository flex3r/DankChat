package com.flxrs.dankchat.preferences.ui

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
import com.flxrs.dankchat.data.twitch.emote.ThirdPartyEmoteType
import com.flxrs.dankchat.databinding.CommandsBottomsheetBinding
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.command.CommandAdapter
import com.flxrs.dankchat.preferences.command.CommandItem
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import com.flxrs.dankchat.utils.extensions.showRestartRequired
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ChatSettingsFragment : MaterialPreferenceFragmentCompat() {

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

    @Suppress("UNCHECKED_CAST")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.chat_settings, rootKey)
        findPreference<SeekBarPreference>(getString(R.string.preference_scrollback_length_key))?.apply {
            summary = DankChatPreferenceStore.correctScrollbackLength(value).toString()
            setOnPreferenceChangeListener { _, newValue ->
                (newValue as? Int)?.let { summary = DankChatPreferenceStore.correctScrollbackLength(it).toString() }
                true
            }
        }
        val allowUnlistedEmotesPreference = findPreference<SwitchPreferenceCompat>(getString(R.string.preference_unlisted_emotes_key))?.apply {
            setOnPreferenceChangeListener { _, _ ->
                view?.showRestartRequired()
                true
            }
        }
        findPreference<MultiSelectListPreference>(getString(R.string.preference_visible_emotes_key))?.apply {
            fun updateUnlistedVisibility(values: Set<String>) {
                val sevenTvEnabled = ThirdPartyEmoteType.SevenTV in ThirdPartyEmoteType.mapFromPreferenceSet(values)
                allowUnlistedEmotesPreference?.isEnabled = sevenTvEnabled
            }
            updateUnlistedVisibility(values)
            setOnPreferenceChangeListener { _, values ->
                updateUnlistedVisibility(values as Set<String>)
                view?.showRestartRequired()
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
                .mapNotNull { Json.decodeOrNull<CommandItem.Entry>(it) }
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
                    .map { Json.encodeToString(it) }
                    .toSet()

                sharedPreferences.edit { putStringSet(key, stringSet) }
            }
            behavior.peekHeight = sheetHeight
            show()
        }

        return true
    }
}