package com.flxrs.dankchat.preferences.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.repo.UserDisplayRepository
import com.flxrs.dankchat.data.twitch.emote.ThirdPartyEmoteType
import com.flxrs.dankchat.databinding.CommandsBottomsheetBinding
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.databinding.UserDisplayBottomSheetBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.command.CommandAdapter
import com.flxrs.dankchat.preferences.command.CommandDto
import com.flxrs.dankchat.preferences.command.CommandDto.Companion.toDto
import com.flxrs.dankchat.preferences.command.CommandDto.Companion.toEntryItem
import com.flxrs.dankchat.preferences.command.CommandItem
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayAdapter
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayDto.Companion.toDto
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayDto.Companion.toEntryItem
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayItem
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import com.flxrs.dankchat.utils.extensions.showRestartRequired
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.util.reflect.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class ChatSettingsFragment : MaterialPreferenceFragmentCompat() {

    @Inject
    lateinit var userDisplayRepository: UserDisplayRepository

    private var bottomSheetDialog: BottomSheetDialog? = null

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

        findPreference<Preference>(getString(R.string.preference_custom_user_display_key))?.apply {
            setOnPreferenceClickListener {
                showUserDisplaySettingsFragment(view)
            }

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
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
        val windowHeight = resources.displayMetrics.heightPixels
        val peekHeight = (windowHeight * 0.6).roundToInt()
        val commands = runCatching {
            sharedPreferences
                .getStringSet(key, emptySet())
                .orEmpty()
                .mapNotNull { Json.decodeOrNull<CommandDto>(it)?.toEntryItem() }
                .plus(CommandItem.AddEntry)
        }.getOrDefault(emptyList())

        val commandAdapter = CommandAdapter(commands.toMutableList())
        val binding = CommandsBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
            commandsList.adapter = commandAdapter
            commandsSheet.updateLayoutParams {
                height = windowHeight
            }
        }

        bottomSheetDialog = BottomSheetDialog(context).apply {
            setContentView(binding.root)
            setOnDismissListener {
                val stringSet = commandAdapter.commands
                    .filterIsInstance<CommandItem.Entry>()
                    .filter { it.trigger.isNotBlank() && it.command.isNotBlank() }
                    .map { Json.encodeToString(it.toDto()) }
                    .toSet()

                sharedPreferences.edit { putStringSet(key, stringSet) }
            }
            behavior.isFitToContents = false
            behavior.peekHeight = peekHeight
            show()
        }

        return true
    }

    private fun showUserDisplaySettingsFragment(root: View): Boolean {
        val context = root.context
        val windowHeight = resources.displayMetrics.heightPixels
        val peekHeight = (windowHeight * 0.6).roundToInt()

        lifecycleScope.launch {
            val userDisplays = listOf(UserDisplayItem.AddEntry) + userDisplayRepository.getAllUserDisplays()
                .map { it.toEntryItem() }
                .sortedByDescending { it.id } // preserve order: since new entries is added at the top of the list
            val idBefore = userDisplays.filterIsInstance<UserDisplayItem.Entry>().map { it.id }


            val userDisplayAdapter = UserDisplayAdapter(userDisplays.toMutableList())
            val binding = UserDisplayBottomSheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
                customUserDisplayList.adapter = userDisplayAdapter
                customUserDisplaySheet.updateLayoutParams {
                    height = windowHeight
                }
            }

            bottomSheetDialog = BottomSheetDialog(context).apply {
                setContentView(binding.root)
                setOnDismissListener {
                    lifecycleScope.launch {
                        var hasChanged = false
                        val entries = userDisplayAdapter.entries
                            .filterIsInstance<UserDisplayItem.Entry>() // filter out "AddEntry"
                            .map { it.toDto() }
                            .also {
                                userDisplayRepository.addUserDisplays(it)
                                if (it.any { entry -> entry.id == 0 }) hasChanged = true

                            }

                        val idAfter = entries.map { it.id }.toSet()
                        idBefore.subtract(idAfter).toList().let { deletedIds ->
                            userDisplayRepository.deleteByIds(deletedIds)
                            if (deletedIds.isNotEmpty()) hasChanged = true
                        }
                        if (hasChanged) view?.showRestartRequired()
                    }
                }
                behavior.isFitToContents = false
                behavior.peekHeight = peekHeight
                show()
            }
        }

        return true
    }
}