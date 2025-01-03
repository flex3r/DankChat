package com.flxrs.dankchat.preferences.appearance

import android.content.Context
import com.flxrs.dankchat.R
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.datastore.PreferenceKeys
import com.flxrs.dankchat.utils.datastore.booleanOrDefault
import com.flxrs.dankchat.utils.datastore.createDataStore
import com.flxrs.dankchat.utils.datastore.dankChatPreferencesMigration
import com.flxrs.dankchat.utils.datastore.intOrDefault
import com.flxrs.dankchat.utils.datastore.mappedStringOrDefault
import com.flxrs.dankchat.utils.datastore.safeData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

@Single
class AppearanceSettingsDataStore(
    context: Context,
    dispatchersProvider: DispatchersProvider,
) {

    private enum class AppearancePreferenceKeys(override val id: Int) : PreferenceKeys {
        Theme(R.string.preference_theme_key),
        TrueDark(R.string.preference_true_dark_theme_key),
        FontSize(R.string.preference_font_size_key),
        KeepScreenOn(R.string.preference_keep_screen_on_key),
        LineSeparator(R.string.preference_line_separator_key),
        CheckeredMessages(R.string.checkered_messages_key),
        ShowInput(R.string.preference_show_input_key),
        AutoDisableInput(R.string.preference_auto_disable_input_key),
        ShowChips(R.string.preference_show_chip_actions_key),
        ShowChangelogs(R.string.preference_show_changelogs_key),
    }

    private val initialMigration = dankChatPreferencesMigration<AppearancePreferenceKeys, AppearanceSettings>(context) { acc, key, value ->
        when (key) {
            AppearancePreferenceKeys.Theme             -> {
                val themeValues = context.resources.getStringArray(R.array.theme_entry_values)
                acc.copy(
                    theme = value.mappedStringOrDefault(acc.theme) { ThemePreference.entries.getOrNull(themeValues.indexOf(it)) }
                )
            }

            AppearancePreferenceKeys.TrueDark          -> acc.copy(trueDarkTheme = value.booleanOrDefault(acc.trueDarkTheme))
            AppearancePreferenceKeys.FontSize          -> acc.copy(fontSize = value.intOrDefault(acc.fontSize))
            AppearancePreferenceKeys.KeepScreenOn      -> acc.copy(keepScreenOn = value.booleanOrDefault(acc.keepScreenOn))
            AppearancePreferenceKeys.LineSeparator     -> acc.copy(lineSeparator = value.booleanOrDefault(acc.lineSeparator))
            AppearancePreferenceKeys.CheckeredMessages -> acc.copy(checkeredMessages = value.booleanOrDefault(acc.checkeredMessages))
            AppearancePreferenceKeys.ShowInput         -> acc.copy(showInput = value.booleanOrDefault(acc.showInput))
            AppearancePreferenceKeys.AutoDisableInput  -> acc.copy(autoDisableInput = value.booleanOrDefault(acc.autoDisableInput))
            AppearancePreferenceKeys.ShowChips         -> acc.copy(showChips = value.booleanOrDefault(acc.showChips))
            AppearancePreferenceKeys.ShowChangelogs    -> acc.copy(showChangelogs = value.booleanOrDefault(acc.showChangelogs))
        }
    }

    private val dataStore = createDataStore(
        fileName = "appearance",
        context = context,
        defaultValue = AppearanceSettings(),
        serializer = AppearanceSettings.serializer(),
        scope = CoroutineScope(dispatchersProvider.io + SupervisorJob()),
        migrations = listOf(initialMigration),
    )

    val settings = dataStore.safeData(AppearanceSettings())
    fun current() = runBlocking { settings.first() }

    suspend fun update(transform: suspend (AppearanceSettings) -> AppearanceSettings) {
        runCatching { dataStore.updateData(transform) }
    }
}
