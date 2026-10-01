package com.flxrs.dankchat.preferences.appearance

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.AutoDisableInput
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.CheckeredMessages
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.CompactChannelInfo
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.FontSize
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.FullscreenButtonOpacity
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.KeepScreenOn
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.LineSeparator
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.RequireFullscreenExitConfirmation
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.ShowCharacterCounter
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.ShowClearInputButton
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.ShowSendButton
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.ShowSendWaitTimer
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.SwipeNavigation
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.Theme
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsInteraction.TrueDarkTheme
import com.flxrs.dankchat.preferences.components.ExpandablePreferenceItem
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.preferences.components.PreferenceCategory
import com.flxrs.dankchat.preferences.components.PreferenceListDialog
import com.flxrs.dankchat.preferences.components.SliderPreferenceItem
import com.flxrs.dankchat.preferences.components.SwitchPreferenceItem
import com.flxrs.dankchat.utils.compose.InputBottomSheet
import com.flxrs.dankchat.utils.compose.rememberModalSheetState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

private const val CUSTOM_SENTINEL = -1f

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<AppearanceSettingsViewModel>()
    val uiState = viewModel.settings.collectAsStateWithLifecycle().value

    AppearanceSettingsContent(
        settings = uiState.settings,
        onInteraction = { viewModel.onInteraction(it) },
        onSuspendingInteraction = { viewModel.onSuspendingInteraction(it) },
        onBack = onBack,
    )
}

@Composable
private fun AppearanceSettingsContent(
    settings: AppearanceSettings,
    onInteraction: (AppearanceSettingsInteraction) -> Unit,
    onSuspendingInteraction: suspend (AppearanceSettingsInteraction) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.preference_appearance_header)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
        ) {
            ThemeCategory(
                theme = settings.theme,
                trueDarkTheme = settings.trueDarkTheme,
                accentColor = settings.accentColor,
                paletteStyle = settings.paletteStyle,
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
            InputCategory(
                autoDisableInput = settings.autoDisableInput,
                showCharacterCounter = settings.showCharacterCounter,
                showSendWaitTimer = settings.showSendWaitTimer,
                showClearInputButton = settings.showClearInputButton,
                showSendButton = settings.showSendButton,
                onInteraction = onInteraction,
            )
            HorizontalDivider(thickness = Dp.Hairline)
            ComponentsCategory(
                swipeNavigation = settings.swipeNavigation,
                compactChannelInfo = settings.compactChannelInfo,
                fullscreenButtonOpacity = settings.fullscreenButtonOpacity,
                requireFullscreenExitConfirmation = settings.requireFullscreenExitConfirmation,
                onInteraction = onInteraction,
            )
            NavigationBarSpacer()
        }
    }
}

@Composable
private fun InputCategory(
    autoDisableInput: Boolean,
    showCharacterCounter: Boolean,
    showSendWaitTimer: Boolean,
    showClearInputButton: Boolean,
    showSendButton: Boolean,
    onInteraction: (AppearanceSettingsInteraction) -> Unit,
) {
    PreferenceCategory(
        title = stringResource(R.string.preference_input_group_title),
    ) {
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_auto_disable_input_title),
            isChecked = autoDisableInput,
            onClick = { onInteraction(AutoDisableInput(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_show_character_counter_title),
            summary = stringResource(R.string.preference_show_character_counter_summary),
            isChecked = showCharacterCounter,
            onClick = { onInteraction(ShowCharacterCounter(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_show_send_wait_timer_title),
            summary = stringResource(R.string.preference_show_send_wait_timer_summary),
            isChecked = showSendWaitTimer,
            onClick = { onInteraction(ShowSendWaitTimer(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_show_clear_input_button_title),
            isChecked = showClearInputButton,
            onClick = { onInteraction(ShowClearInputButton(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_show_send_button_title),
            isChecked = showSendButton,
            onClick = { onInteraction(ShowSendButton(it)) },
        )
    }
}

@Composable
private fun ComponentsCategory(
    swipeNavigation: Boolean,
    compactChannelInfo: Boolean,
    fullscreenButtonOpacity: Float,
    requireFullscreenExitConfirmation: Boolean,
    onInteraction: (AppearanceSettingsInteraction) -> Unit,
) {
    val opacityPresets = remember { listOf(0.25f, 0.50f, 0.75f, 1.0f) }
    val opacityLabels = remember { listOf("25%", "50%", "75%", "100%") }
    val currentPercentage = (fullscreenButtonOpacity * 100).roundToInt()
    val isCustom = fullscreenButtonOpacity !in opacityPresets
    val summary = stringResource(R.string.preference_fullscreen_button_opacity_summary, currentPercentage)

    var showCustomInput by remember { mutableStateOf(false) }

    PreferenceCategory(
        title = stringResource(R.string.preference_components_group_title),
    ) {
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_swipe_navigation_title),
            summary = stringResource(R.string.preference_swipe_navigation_summary),
            isChecked = swipeNavigation,
            onClick = { onInteraction(SwipeNavigation(it)) },
        )

        SwitchPreferenceItem(
            title = stringResource(R.string.preference_compact_channel_info_title),
            summary = stringResource(R.string.preference_compact_channel_info_summary),
            isChecked = compactChannelInfo,
            onClick = { onInteraction(CompactChannelInfo(it)) },
        )

        val customLabel = stringResource(R.string.preference_fullscreen_button_opacity_custom)
        val allValues = remember { (opacityPresets + CUSTOM_SENTINEL).toImmutableList() }
        val allLabels = remember(customLabel) { (opacityLabels + customLabel).toImmutableList() }
        val selected = if (isCustom) CUSTOM_SENTINEL else fullscreenButtonOpacity

        PreferenceListDialog(
            title = stringResource(R.string.preference_fullscreen_button_opacity_title),
            summary = summary,
            values = allValues,
            entries = allLabels,
            selected = selected,
            onChange = { value ->
                when (value) {
                    CUSTOM_SENTINEL -> showCustomInput = true
                    else -> onInteraction(FullscreenButtonOpacity(value))
                }
            },
        )

        SwitchPreferenceItem(
            title = stringResource(R.string.preference_require_fullscreen_exit_confirmation_title),
            summary = stringResource(R.string.preference_require_fullscreen_exit_confirmation_summary),
            isChecked = requireFullscreenExitConfirmation,
            onClick = { onInteraction(RequireFullscreenExitConfirmation(it)) },
        )
    }

    if (showCustomInput) {
        val validationError = stringResource(R.string.preference_fullscreen_button_opacity_validation)
        InputBottomSheet(
            title = stringResource(R.string.preference_fullscreen_button_opacity_title),
            hint = stringResource(R.string.preference_fullscreen_button_opacity_hint),
            defaultValue = currentPercentage.toString(),
            keyboardType = KeyboardType.Number,
            validate = { input ->
                val parsed = input.toIntOrNull()
                when {
                    parsed == null || parsed !in 1..100 -> validationError
                    else -> null
                }
            },
            onConfirm = { input ->
                val parsed = input.toIntOrNull() ?: return@InputBottomSheet
                onInteraction(FullscreenButtonOpacity(parsed / 100f))
                showCustomInput = false
            },
            onDismiss = { showCustomInput = false },
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
        title = stringResource(R.string.preference_display_group_title),
    ) {
        val context = LocalContext.current
        var value by remember(fontSize) { mutableFloatStateOf(fontSize.toFloat()) }
        val summary = remember(value) { getFontSizeSummary(value.roundToInt(), context) }
        SliderPreferenceItem(
            title = stringResource(R.string.preference_font_size_title),
            value = value,
            range = 10f..40f,
            onDrag = { value = it },
            onDragFinish = { onInteraction(FontSize(value.roundToInt())) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeCategory(
    theme: ThemePreference,
    trueDarkTheme: Boolean,
    accentColor: AccentColor?,
    paletteStyle: PaletteStylePreference,
    onInteraction: suspend (AppearanceSettingsInteraction) -> Unit,
) {
    val activity = LocalActivity.current as? ComponentActivity
    val scope = rememberCoroutineScope()
    val themeState = rememberThemeState(theme, trueDarkTheme, isSystemInDarkTheme())
    val hasCustomAccent = accentColor != null
    PreferenceCategory(
        title = stringResource(R.string.preference_theme_title),
    ) {
        PreferenceListDialog(
            title = stringResource(R.string.preference_theme_title),
            summary = themeState.summary,
            isEnabled = themeState.themeSwitcherEnabled,
            values = themeState.values,
            entries = themeState.entries,
            selected = themeState.preference,
            onChange = { preference ->
                scope.launch {
                    onInteraction(Theme(preference))
                    setDarkMode(activity, preference)
                }
            },
        )
        AccentColorPicker(
            selectedColor = accentColor,
            onColorSelect = { color ->
                scope.launch { onInteraction(AppearanceSettingsInteraction.SetAccentColor(color)) }
            },
        )
        PaletteStyleDialog(
            paletteStyle = paletteStyle,
            showSystemDefault = !hasCustomAccent,
            onChange = { scope.launch { onInteraction(AppearanceSettingsInteraction.SetPaletteStyle(it)) } },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_true_dark_theme_title),
            summary = stringResource(R.string.preference_true_dark_theme_summary),
            isChecked = themeState.trueDarkPreference,
            isEnabled = themeState.trueDarkEnabled,
            onClick = { scope.launch { onInteraction(TrueDarkTheme(it)) } },
        )
    }
}

@Immutable
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
private fun rememberThemeState(
    theme: ThemePreference,
    trueDark: Boolean,
    systemDarkMode: Boolean,
): ThemeState {
    LocalContext.current
    val defaultEntries = stringArrayResource(R.array.theme_entries).toImmutableList()
    // minSdk 30 always supports light mode and system dark mode
    stringResource(R.string.preference_dark_theme_entry_title)
    stringResource(R.string.preference_light_theme_entry_title)

    val (entries, values) =
        remember {
            defaultEntries to ThemePreference.entries.toImmutableList()
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

private fun getFontSizeSummary(
    value: Int,
    context: Context,
): String = when {
    value < 13 -> context.getString(R.string.preference_font_size_summary_very_small)
    value in 13..17 -> context.getString(R.string.preference_font_size_summary_small)
    value in 18..22 -> context.getString(R.string.preference_font_size_summary_large)
    else -> context.getString(R.string.preference_font_size_summary_very_large)
}

@Composable
private fun AccentColorPicker(
    selectedColor: AccentColor?,
    onColorSelect: (AccentColor?) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.preference_accent_color_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when (selectedColor) {
                null -> stringResource(R.string.preference_accent_color_summary_default)
                else -> stringResource(selectedColor.labelRes)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // System default option
            AccentColorCircle(
                color = null,
                isSelected = selectedColor == null,
                onClick = { onColorSelect(null) },
            )
            // Preset colors
            AccentColor.entries.forEach { accent ->
                AccentColorCircle(
                    color = accent,
                    isSelected = selectedColor == accent,
                    onClick = { onColorSelect(accent) },
                )
            }
        }
    }
}

@Composable
private fun AccentColorCircle(
    color: AccentColor?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val circleSize = 40.dp
    val borderColor = MaterialTheme.colorScheme.outline
    Box(
        modifier =
            Modifier
                .size(circleSize)
                .clip(CircleShape)
                .then(
                    when {
                        isSelected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else -> Modifier
                    },
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (color != null) {
            Box(
                modifier =
                    Modifier
                        .size(circleSize - 4.dp)
                        .background(color.seedColor, CircleShape),
            )
        } else {
            // System default: outlined circle with auto icon
            Box(
                modifier =
                    Modifier
                        .size(circleSize - 4.dp)
                        .border(1.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = stringResource(R.string.preference_accent_color_summary_default),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = when (color) {
                    null -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surface
                },
            )
        }
    }
}

@Composable
private fun PaletteStyleDialog(
    paletteStyle: PaletteStylePreference,
    showSystemDefault: Boolean,
    onChange: (PaletteStylePreference) -> Unit,
) {
    val scope = rememberCoroutineScope()
    ExpandablePreferenceItem(
        title = stringResource(R.string.preference_palette_style_title),
        summary = stringResource(paletteStyle.labelRes),
    ) {
        val sheetState = rememberModalSheetState()
        val standardStyles = remember(showSystemDefault) {
            PaletteStylePreference.entries.filter {
                it.isStandard && (showSystemDefault || it != PaletteStylePreference.SystemDefault)
            }
        }
        val extraStyles = remember { PaletteStylePreference.entries.filter { !it.isStandard } }
        var showExtra by remember { mutableStateOf(!paletteStyle.isStandard) }

        ModalBottomSheet(
            onDismissRequest = ::dismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            standardStyles.forEach { style ->
                PaletteStyleRow(
                    style = style,
                    isSelected = paletteStyle == style,
                    onClick = {
                        onChange(style)
                        scope.launch {
                            sheetState.hide()
                            dismiss()
                        }
                    },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { showExtra = !showExtra }
                        .padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Icon(
                    imageVector = if (showExtra) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.palette_style_more),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            AnimatedVisibility(visible = showExtra) {
                Column {
                    extraStyles.forEach { style ->
                        PaletteStyleRow(
                            style = style,
                            isSelected = paletteStyle == style,
                            onClick = {
                                onChange(style)
                                scope.launch {
                                    sheetState.hide()
                                    dismiss()
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PaletteStyleRow(
    style: PaletteStylePreference,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = isSelected,
                    onClick = onClick,
                    interactionSource = interactionSource,
                    indication = ripple(),
                ).padding(horizontal = 16.dp),
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            interactionSource = interactionSource,
        )
        Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                text = stringResource(style.labelRes),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(style.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun setDarkMode(
    activity: ComponentActivity?,
    themePreference: ThemePreference,
) {
    AppCompatDelegate.setDefaultNightMode(
        when (themePreference) {
            ThemePreference.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemePreference.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            ThemePreference.Light -> AppCompatDelegate.MODE_NIGHT_NO
        },
    )

    activity ?: return
    val systemDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val isDark = when (themePreference) {
        ThemePreference.Dark -> true
        ThemePreference.Light -> false
        ThemePreference.System -> systemDark
    }
    val barStyle = when {
        isDark -> SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else -> SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
    }
    activity.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
}
