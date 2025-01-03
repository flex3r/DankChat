package com.flxrs.dankchat.preferences.appearance

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.findNavController
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.AutoDisableInput
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.CheckeredMessages
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.FontSize
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.KeepScreenOn
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.LineSeparator
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.ShowChangelogs
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.ShowChips
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.ShowInput
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.Theme
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.TrueDarkTheme
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.preferences.components.PreferenceCategory
import com.flxrs.dankchat.preferences.components.PreferenceCategoryTitle
import com.flxrs.dankchat.preferences.components.PreferenceItem
import com.flxrs.dankchat.preferences.components.PreferenceListDialog
import com.flxrs.dankchat.preferences.components.SliderPreferenceItem
import com.flxrs.dankchat.preferences.components.SwitchPreferenceItem
import com.flxrs.dankchat.theme.DankChatTheme
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

class AppearanceSettingsFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        returnTransition = MaterialFadeThrough()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        (view.parent as? View)?.doOnPreDraw { startPostponedEnterTransition() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val viewModel = koinViewModel<AppearanceSettingsViewModel>()
                val settings = viewModel.settings.collectAsStateWithLifecycle().value

                DankChatTheme {
                    AppearanceSettings(
                        settings = settings,
                        onInteraction = { viewModel.onInteraction(it) },
                        onSuspendingInteraction = { viewModel.onSuspendingInteraction(it) },
                        onBackPressed = { findNavController().popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettings(
    settings: AppearanceSettings,
    onInteraction: (AppearanceSettingsInteraction) -> Unit,
    onSuspendingInteraction: suspend (AppearanceSettingsInteraction) -> Unit,
    onBackPressed: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        contentWindowInsets = WindowInsets(bottom = 0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.preference_appearance_header)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackPressed,
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ThemeCategory(
                theme = settings.theme,
                trueDarkTheme = settings.trueDarkTheme,
                onInteraction = onSuspendingInteraction,
            )
            HorizontalDivider(thickness = Dp.Hairline)
            DisplayCategory(
                fontSize = settings.fontSize,
                keepScreenOn = settings.keepScreenOn,
                lineSeparator = settings.lineSeparator,
                checkeredMessages = settings.checkeredMessages,
                onInteraction = onInteraction,
            )
            HorizontalDivider(thickness = Dp.Hairline)
            ComponentsCategory(
                showInput = settings.showInput,
                autoDisableInput = settings.autoDisableInput,
                showChips = settings.showChips,
                showChangelogs = settings.showChangelogs,
                onInteraction = onInteraction,
            )
            NavigationBarSpacer()
        }
    }
}

@Composable
private fun ComponentsCategory(
    showInput: Boolean,
    autoDisableInput: Boolean,
    showChips: Boolean,
    showChangelogs: Boolean,
    onInteraction: (AppearanceSettingsInteraction) -> Unit,
) {
    PreferenceCategory(
        title = { PreferenceCategoryTitle(text = stringResource(R.string.preference_components_group_title)) }
    ) {
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_show_input_title),
            summary = stringResource(R.string.preference_show_input_summary),
            isChecked = showInput,
            onClick = { onInteraction(ShowInput(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_auto_disable_input_title),
            isEnabled = showInput,
            isChecked = autoDisableInput,
            onClick = { onInteraction(AutoDisableInput(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_show_chip_actions_title),
            summary = stringResource(R.string.preference_show_chip_actions_summary),
            isChecked = showChips,
            onClick = { onInteraction(ShowChips(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_show_changelogs),
            isChecked = showChangelogs,
            onClick = { onInteraction(ShowChangelogs(it)) },
        )
    }
}

@Composable
private fun DisplayCategory(
    fontSize: Int,
    keepScreenOn: Boolean,
    lineSeparator: Boolean,
    checkeredMessages: Boolean,
    onInteraction: (AppearanceSettingsInteraction) -> Unit,
) {
    PreferenceCategory(
        title = { PreferenceCategoryTitle(stringResource(R.string.preference_display_group_title)) }
    ) {
        val context = LocalContext.current
        var value by remember(fontSize) { mutableFloatStateOf(fontSize.toFloat()) }
        val summary = remember(value) { getFontSizeSummary(value.roundToInt(), context) }
        SliderPreferenceItem(
            title = stringResource(R.string.preference_font_size_title),
            value = value,
            range = 10f..40f,
            onDrag = { value = it },
            onDragFinished = { onInteraction(FontSize(value.roundToInt())) },
            summary = summary,
        )

        SwitchPreferenceItem(
            title = stringResource(R.string.preference_keep_screen_on_title),
            isChecked = keepScreenOn,
            onClick = { onInteraction(KeepScreenOn(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_line_separator_title),
            isChecked = lineSeparator,
            onClick = { onInteraction(LineSeparator(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_checkered_lines_title),
            summary = stringResource(R.string.preference_checkered_lines_summary),
            isChecked = checkeredMessages,
            onClick = { onInteraction(CheckeredMessages(it)) },
        )
    }
}

@Composable
private fun ThemeCategory(
    theme: ThemePreference,
    trueDarkTheme: Boolean,
    onInteraction: suspend (AppearanceSettingsInteraction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var themeDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val themeState = rememberThemeState(theme, trueDarkTheme, isSystemInDarkTheme())
    PreferenceCategory(
        title = { PreferenceCategoryTitle(stringResource(R.string.preference_theme_title)) }
    ) {
        PreferenceItem(
            title = stringResource(R.string.preference_theme_title),
            summary = themeState.summary,
            onClick = { themeDialog = true },
            isEnabled = themeState.themeSwitcherEnabled,
        )
        val activity = LocalActivity.current
        if (themeDialog) {
            PreferenceListDialog(
                values = themeState.values,
                entries = themeState.entries,
                selected = themeState.preference,
                onDismissRequested = { themeDialog = false },
                onChanged = {
                    scope.launch {
                        activity ?: return@launch
                        onInteraction(Theme(it))
                        sheetState.hide()
                        themeDialog = false
                        setDarkMode(it, activity)
                    }
                }
            )
        }

        SwitchPreferenceItem(
            title = stringResource(R.string.preference_true_dark_theme_title),
            summary = stringResource(R.string.preference_true_dark_theme_summary),
            isChecked = themeState.trueDarkPreference,
            isEnabled = themeState.trueDarkEnabled,
            onClick = {
                scope.launch {
                    activity ?: return@launch
                    onInteraction(TrueDarkTheme(it))
                    ActivityCompat.recreate(activity)
                }
            }
        )
    }
}

data class ThemeState(
    val preference: ThemePreference,
    val summary: String,
    val trueDarkPreference: Boolean,
    val values: ImmutableList<ThemePreference>,
    val entries: ImmutableList<String>,
    val themeSwitcherEnabled: Boolean,
    val trueDarkEnabled: Boolean,
)

@Composable
@Stable
private fun rememberThemeState(theme: ThemePreference, trueDark: Boolean, systemDarkMode: Boolean): ThemeState {
    val context = LocalContext.current
    val defaultEntries = stringArrayResource(R.array.theme_entries).toImmutableList()
    val darkThemeTitle = stringResource(R.string.preference_dark_theme_entry_title)
    val lightThemeTitle = stringResource(R.string.preference_light_theme_entry_title)
    val shouldDisable = remember {
        val uiModeManager = getSystemService(context, UiModeManager::class.java)
        val isTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 && !isTv
    }
    if (shouldDisable) {
        return ThemeState(
            preference = ThemePreference.Dark,
            summary = darkThemeTitle,
            trueDarkPreference = trueDark,
            values = listOf(ThemePreference.Dark).toImmutableList(),
            entries = listOf(darkThemeTitle).toImmutableList(),
            themeSwitcherEnabled = false,
            trueDarkEnabled = true,
        )
    }

    val (entries, values) = remember {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> Pair(
                listOf(darkThemeTitle, lightThemeTitle).toImmutableList(),
                listOf(ThemePreference.Dark, ThemePreference.Light).toImmutableList(),
            )

            else                                          -> defaultEntries to ThemePreference.entries.toImmutableList()
        }
    }

    return remember(theme, trueDark) {
        val selected = if (theme in values) theme else ThemePreference.Dark
        val trueDarkEnabled = selected == ThemePreference.Dark || (selected == ThemePreference.System && systemDarkMode)
        ThemeState(
            preference = selected,
            summary = entries[values.indexOf(selected)],
            trueDarkPreference = trueDarkEnabled && trueDark,
            values = values,
            entries = entries,
            themeSwitcherEnabled = true,
            trueDarkEnabled = trueDarkEnabled,
        )
    }
}

private fun getFontSizeSummary(value: Int, context: Context): String {
    return when {
        value < 13 -> context.getString(R.string.preference_font_size_summary_very_small)
        value in 13..17 -> context.getString(R.string.preference_font_size_summary_small)
        value in 18..22 -> context.getString(R.string.preference_font_size_summary_large)
        else -> context.getString(R.string.preference_font_size_summary_very_large)
    }
}

private fun setDarkMode(themePreference: ThemePreference, activity: Activity) {
    AppCompatDelegate.setDefaultNightMode(
        when (themePreference) {
            ThemePreference.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemePreference.Dark   -> AppCompatDelegate.MODE_NIGHT_YES
            else                   -> AppCompatDelegate.MODE_NIGHT_NO
        }
    )
    ActivityCompat.recreate(activity)
}
