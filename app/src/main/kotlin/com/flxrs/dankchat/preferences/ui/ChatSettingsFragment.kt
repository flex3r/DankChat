package com.flxrs.dankchat.preferences.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.preference.*
import com.flxrs.dankchat.R
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
import com.flxrs.dankchat.preferences.ui.userdisplay.UserDisplayAdapter
import com.flxrs.dankchat.preferences.ui.userdisplay.UserDisplayEvent
import com.flxrs.dankchat.preferences.ui.userdisplay.UserDisplayViewModel
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import com.flxrs.dankchat.utils.extensions.expand
import com.flxrs.dankchat.utils.extensions.showRestartRequired
import com.flxrs.dankchat.utils.extensions.showShortSnackbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.util.reflect.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@AndroidEntryPoint
class ChatSettingsFragment : MaterialPreferenceFragmentCompat() {

    private val userDisplayViewModel: UserDisplayViewModel by viewModels()

    private var bottomSheetDialog: BottomSheetDialog? = null
    private var bottomSheetBinding: UserDisplayBottomSheetBinding? = null

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

        val userDisplayAdapter = UserDisplayAdapter(
            onAddItem = userDisplayViewModel::saveChangesAndCreateNewBlank,
            onDeleteItem = userDisplayViewModel::deleteEntry,
        )

        findPreference<Preference>(getString(R.string.preference_custom_user_display_key))?.apply {
            setOnPreferenceClickListener {
                bottomSheetBinding = UserDisplayBottomSheetBinding.inflate(LayoutInflater.from(view.context), view as? ViewGroup, false)
                showUserDisplaySettingsFragment(userDisplayAdapter)
                true
            }
        }

        collectFlow(userDisplayViewModel.userDisplays) { userDisplayAdapter.submitList(it) }
        collectFlow(userDisplayViewModel.events) { event ->
            when (event) {
                is UserDisplayEvent.ItemRemoved -> bottomSheetBinding?.root?.showShortSnackbar(getString(R.string.item_removed)) {
                    setAction(getString(R.string.undo)) {
                        userDisplayViewModel.saveChangesAndAddEntry(userDisplayAdapter.currentList, event.item)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
        bottomSheetBinding = null
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
        val binding = CommandsBottomsheetBinding
            .inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
                commandsList.adapter = commandAdapter
                commandsSheet.updateLayoutParams {
                    height = windowHeight
                }
                ViewCompat.setOnApplyWindowInsetsListener(commandsList) { v, insets ->
                    v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                    WindowInsetsCompat.CONSUMED
                }
            }

        bottomSheetDialog = BottomSheetDialog(context).apply {
            setContentView(binding.root)
            setOnDismissListener {
                val stringSet =
                    commandAdapter.commands
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

    private fun showUserDisplaySettingsFragment(adapter: UserDisplayAdapter) {
        val binding = bottomSheetBinding ?: return
        with(binding) {
            customUserDisplaySheet.updateLayoutParams {
                height = resources.displayMetrics.heightPixels
            }
            customUserDisplayList.adapter = adapter
            ViewCompat.setOnApplyWindowInsetsListener(customUserDisplayList) { v, insets ->
                v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                WindowInsetsCompat.CONSUMED
            }
        }

        bottomSheetDialog = BottomSheetDialog(requireContext()).apply {
            setOnDismissListener {
                userDisplayViewModel.saveChanges(adapter.currentList)
                bottomSheetDialog = null
                bottomSheetBinding = null
            }
            setContentView(binding.root)
            behavior.skipCollapsed = true
            behavior.isFitToContents = false
            behavior.expand()
            show()
        }
    }
}
